package com.example.agenticaiapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agenticaiapp.audio.AudioRecorder
import com.example.agenticaiapp.network.AgentRequest
import com.example.agenticaiapp.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val recorder = AudioRecorder(application)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var nextId: Long = 1L

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
    }

    fun sendText() {
        val draft = _state.value.draft.trim()
        if (draft.isEmpty() || _state.value.isSending) return
        _state.update { it.copy(draft = "") }
        appendUserMessage(draft, MessageKind.TEXT)
        dispatch(
            action = ActionRouter.classifyText(draft),
            prompt = draft,
            audioBase64 = null,
        )
    }

    fun toggleRecording() {
        if (_state.value.isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        try {
            recorder.start()
            _state.update { it.copy(isRecording = true, errorMessage = null) }
        } catch (t: Throwable) {
            _state.update { it.copy(errorMessage = "Could not start recording: ${t.message}") }
        }
    }

    private fun stopRecording() {
        val base64 = recorder.stop()
        _state.update { it.copy(isRecording = false) }
        if (base64.isNullOrEmpty()) {
            _state.update { it.copy(errorMessage = "Recording was empty.") }
            return
        }
        appendUserMessage("Voice prompt", MessageKind.VOICE)
        dispatch(
            action = ActionRouter.classifyVoice(),
            prompt = null,
            audioBase64 = base64,
        )
    }

    fun cancelRecording() {
        recorder.cancel()
        _state.update { it.copy(isRecording = false) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun appendUserMessage(text: String, kind: MessageKind) {
        val msg = ChatMessage(id = nextId++, role = MessageRole.USER, text = text, kind = kind)
        _state.update { it.copy(messages = it.messages + msg) }
    }

    private fun appendAssistantMessage(text: String, videoCard: VideoCardData? = null): Long {
        val id = nextId++
        val msg = ChatMessage(
            id = id,
            role = MessageRole.ASSISTANT,
            text = text,
            videoCard = videoCard,
        )
        _state.update { it.copy(messages = it.messages + msg) }
        return id
    }

    private fun updateAssistantText(id: Long, text: String) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(text = text) else it
                }
            )
        }
    }

    private fun attachVideoCard(id: Long, card: VideoCardData) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(videoCard = card) else it
                }
            )
        }
    }

    /** Re-emit the open-video event so tapping the in-chat card replays the intent. */
    fun openVideo(url: String) {
        viewModelScope.launch { _events.emit(ChatEvent.OpenYouTube(url)) }
    }

    /**
     * Either fire a local action (e.g. opening YouTube) or forward the prompt
     * to the backend. The action stub keeps the backend call out of the loop
     * so we don't show a "Thinking…" bubble when the answer is just an intent.
     */
    private fun dispatch(action: AgentAction, prompt: String?, audioBase64: String?) {
        when (action) {
            is AgentAction.PlayYouTube -> {
                val card = StubVideoCatalog.cardFor(action.url)
                viewModelScope.launch {
                    // Brief "thinking" beat before streaming starts — feels alive.
                    delay(THINKING_DELAY_MS)

                    val id = appendAssistantMessage(text = "")
                    streamText(id, card.description)
                    attachVideoCard(id, card)

                    // Pause so the audience can see the card before YouTube opens.
                    delay(CARD_REVEAL_DELAY_MS)
                    _events.emit(ChatEvent.OpenYouTube(action.url))
                }
            }
            AgentAction.None -> sendToBackend(prompt, audioBase64)
        }
    }

    /**
     * Streams [text] into the message with [id] one word at a time, so the
     * chat visibly grows downward and exercises the auto-scroll behavior —
     * mirrors Gemini's word-by-word reveal.
     */
    private suspend fun streamText(id: Long, text: String) {
        val tokens = text.split(' ')
        val builder = StringBuilder()
        for ((index, token) in tokens.withIndex()) {
            if (index > 0) builder.append(' ')
            builder.append(token)
            updateAssistantText(id, builder.toString())
            delay(STREAM_WORD_DELAY_MS)
        }
    }

    private fun sendToBackend(prompt: String?, audioBase64: String?) {
        val loadingId = nextId++
        val loadingMsg = ChatMessage(
            id = loadingId,
            role = MessageRole.ASSISTANT,
            text = "",
            isLoading = true,
        )
        _state.update { it.copy(messages = it.messages + loadingMsg, isSending = true) }

        viewModelScope.launch {
            val reply = runCatching {
                withContext(Dispatchers.IO) {
                    ApiClient.agentApi.sendPrompt(
                        AgentRequest(prompt = prompt, audioBase64 = audioBase64)
                    )
                }
            }
            val replyText = reply.fold(
                onSuccess = { it.reply },
                onFailure = {
                    "(Backend not connected) Received ${if (audioBase64 != null) "voice" else "text"} prompt."
                },
            )

            _state.update { current ->
                val updated = current.messages.map {
                    if (it.id == loadingId) it.copy(text = replyText, isLoading = false) else it
                }
                current.copy(messages = updated, isSending = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorder.cancel()
    }

    private companion object {
        const val THINKING_DELAY_MS = 350L
        const val STREAM_WORD_DELAY_MS = 55L
        const val CARD_REVEAL_DELAY_MS = 700L
    }
}
