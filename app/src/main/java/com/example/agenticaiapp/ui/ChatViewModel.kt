package com.example.agenticaiapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agenticaiapp.audio.AudioRecorder
import com.example.agenticaiapp.network.AgentRequest
import com.example.agenticaiapp.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val recorder = AudioRecorder(application)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var nextId: Long = 1L

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
    }

    fun sendText() {
        val draft = _state.value.draft.trim()
        if (draft.isEmpty() || _state.value.isSending) return
        _state.update { it.copy(draft = "") }
        appendUserMessage(draft, MessageKind.TEXT)
        sendToBackend(prompt = draft, audioBase64 = null)
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
        sendToBackend(prompt = null, audioBase64 = base64)
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
                    // Backend not implemented yet — show a clear placeholder reply
                    // so the rest of the flow can be exercised end-to-end.
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
}
