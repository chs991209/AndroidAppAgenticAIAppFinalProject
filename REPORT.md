# AgenticAIApp — Code Walkthrough

A Gemini-style Android client that collects a user's **text prompt** and **voice prompt**, then sends them to a backend agent. The backend is not yet implemented; the network layer is in place and the UI gracefully falls back to a placeholder reply so the full flow can be exercised end-to-end.

---

## 1. Architecture at a Glance

```
app/src/main/java/com/example/agenticaiapp/
├── MainActivity.kt              # Hosts Compose + runtime mic permission
├── audio/
│   └── AudioRecorder.kt         # MediaRecorder → m4a → Base64
├── network/
│   ├── AgentApi.kt              # Retrofit interface (POST /agent)
│   ├── ApiClient.kt             # Retrofit + OkHttp + Moshi singleton
│   └── Models.kt                # AgentRequest, AgentResponse
└── ui/
    ├── ChatModels.kt            # ChatMessage, ChatUiState enums
    ├── ChatViewModel.kt         # State holder, send/record orchestration
    ├── ChatScreen.kt            # Compose UI (top bar / list / input bar)
    └── theme/
        ├── Color.kt
        └── Theme.kt
```

**Stack**

| Layer        | Library                                                |
| ------------ | ------------------------------------------------------ |
| UI           | Jetpack Compose, Material 3, Material Icons Extended   |
| State        | `AndroidViewModel`, `StateFlow`, Kotlin Coroutines     |
| Audio        | `android.media.MediaRecorder` + `android.util.Base64`  |
| Network      | Retrofit 2, OkHttp logging interceptor, Moshi (Kotlin) |
| Build        | AGP 9.1.1 (built-in Kotlin), Gradle 9.3.1, Java 17     |

**Data flow**

```
User typing  ──▶ ChatViewModel.sendText() ──┐
                                            ▼
User mic    ──▶ AudioRecorder.stop()        sendToBackend()
                  (returns Base64)          ──▶ ApiClient.agentApi.sendPrompt(AgentRequest)
                                                   │
                                                   ▼
                                            AgentResponse.reply ─▶ UI bubble
```

---

## 2. Manifest & Permissions

`app/src/main/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

- `INTERNET` is required for Retrofit calls.
- `RECORD_AUDIO` is a **runtime** permission (dangerous), so it is requested on first mic tap — see `MainActivity.handleMicTap()`.

---

## 3. Audio Recording — `audio/AudioRecorder.kt`

A small wrapper around `MediaRecorder` that produces an AAC-in-MP4 (`.m4a`) file and returns its **Base64 string** ready for transport.

### 3.1 `start()` — begin recording

```kotlin
fun start() {
    if (recorder != null) return                                     // ❶ guard against double-start

    val file = File(context.cacheDir, "voice_prompt_${System.currentTimeMillis()}.m4a")
    outputFile = file

    val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)                                       // ❷ API 31+ requires Context
    } else {
        @Suppress("DEPRECATION") MediaRecorder()
    }

    newRecorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(128_000)                             // ❸ 128 kbps AAC
        setAudioSamplingRate(44_100)
        setOutputFile(file.absolutePath)
        prepare()
        start()
    }
    recorder = newRecorder
}
```

❶ Idempotent — calling `start()` while already recording is a no-op.
❷ The `MediaRecorder(Context)` constructor is mandatory from Android 12 (API 31).
❸ AAC@128 kbps, 44.1 kHz is small, broadly supported, and high enough quality for STT/agent input.

### 3.2 `stop()` — finalize and return Base64

```kotlin
fun stop(): String? {
    val current = recorder ?: return null
    return try {
        current.stop()
        current.release()
        recorder = null
        val file = outputFile ?: return null
        val bytes = file.readBytes()
        Base64.encodeToString(bytes, Base64.NO_WRAP)                 // ❶ single-line Base64
    } catch (t: Throwable) {
        current.runCatching { release() }
        recorder = null
        null
    } finally {
        outputFile?.delete()                                         // ❷ never leave temp files
        outputFile = null
    }
}
```

❶ `Base64.NO_WRAP` produces a string without `\n` — safe to drop straight into a JSON field.
❷ The cache file is always cleaned up, even if `stop()` throws.

### 3.3 `cancel()` — discard without sending
Used when the screen is destroyed mid-recording (`ChatViewModel.onCleared()`). Releases the recorder and deletes the temp file.

---

## 4. Network Layer — `network/`

### 4.1 Request & response shapes — `Models.kt`

```kotlin
data class AgentRequest(
    val prompt: String? = null,
    val audioBase64: String? = null,
)

data class AgentResponse(
    val reply: String,
)
```

The backend receives **exactly** what the spec asked for:

```json
{ "prompt": "How is the weather today?" }
```

or for voice:

```json
{ "audioBase64": "AAAAGGZ0eXBtcDQy…" }
```

### 4.2 Retrofit interface — `AgentApi.kt`

```kotlin
interface AgentApi {
    @POST("agent")
    suspend fun sendPrompt(@Body request: AgentRequest): AgentResponse
}
```

`suspend` lets the view-model `await` the response from a coroutine without any callback plumbing.

### 4.3 Retrofit client — `ApiClient.kt`

```kotlin
private const val BASE_URL = "https://example.com/api/"   // ← replace when backend is ready

private val okHttpClient: OkHttpClient by lazy {
    val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}

val agentApi: AgentApi by lazy {
    Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(AgentApi::class.java)
}
```

- Singleton (`object ApiClient`) — one OkHttp pool for the whole process.
- Read/write timeouts are generous because the backend will be doing LLM inference.
- The interceptor logs full request/response bodies during development.
- **Only `BASE_URL` needs to change** when the backend goes live.

---

## 5. State Model — `ui/ChatModels.kt`

```kotlin
enum class MessageRole { USER, ASSISTANT }
enum class MessageKind { TEXT, VOICE }

data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
    val kind: MessageKind = MessageKind.TEXT,
    val isLoading: Boolean = false,        // assistant placeholder while waiting
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val isRecording: Boolean = false,
    val errorMessage: String? = null,
)
```

A single `ChatUiState` is exposed as `StateFlow<ChatUiState>` — Compose recomposes whatever depends on it.

---

## 6. ViewModel — `ui/ChatViewModel.kt`

### 6.1 `onDraftChange(value)` — update the text field
```kotlin
fun onDraftChange(value: String) { _state.update { it.copy(draft = value) } }
```

### 6.2 `sendText()` — POST the text prompt
```kotlin
fun sendText() {
    val draft = _state.value.draft.trim()
    if (draft.isEmpty() || _state.value.isSending) return            // ❶ guard
    _state.update { it.copy(draft = "") }                            // ❷ optimistically clear input
    appendUserMessage(draft, MessageKind.TEXT)                       // ❸ show user bubble immediately
    sendToBackend(prompt = draft, audioBase64 = null)                // ❹ fire the request
}
```

### 6.3 `toggleRecording()` — single mic button
```kotlin
fun toggleRecording() {
    if (_state.value.isRecording) stopRecording() else startRecording()
}
```

The mic button is **toggle-style**: first tap starts recording, second tap stops and sends. This mirrors how Gemini's voice button feels.

### 6.4 `startRecording()` / `stopRecording()`
```kotlin
private fun stopRecording() {
    val base64 = recorder.stop()
    _state.update { it.copy(isRecording = false) }
    if (base64.isNullOrEmpty()) {
        _state.update { it.copy(errorMessage = "Recording was empty.") }
        return
    }
    appendUserMessage("Voice prompt", MessageKind.VOICE)
    sendToBackend(prompt = null, audioBase64 = base64)               // ❶ POST audioBase64
}
```

❶ Once stopped, the encoded audio is shipped to the same endpoint — only the request field changes.

### 6.5 `sendToBackend(prompt, audioBase64)` — the core call

```kotlin
private fun sendToBackend(prompt: String?, audioBase64: String?) {
    val loadingId = nextId++
    val loadingMsg = ChatMessage(loadingId, MessageRole.ASSISTANT, "", isLoading = true)
    _state.update { it.copy(messages = it.messages + loadingMsg, isSending = true) }    // ❶

    viewModelScope.launch {
        val reply = runCatching {
            withContext(Dispatchers.IO) {                                                // ❷
                ApiClient.agentApi.sendPrompt(
                    AgentRequest(prompt = prompt, audioBase64 = audioBase64)
                )
            }
        }
        val replyText = reply.fold(
            onSuccess = { it.reply },
            onFailure = {                                                                // ❸
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
```

❶ Add a **placeholder assistant bubble** immediately (`isLoading = true`) so the UI shows a "Thinking…" spinner instantly.
❷ Network on `Dispatchers.IO`; UI updates back on the main thread because `viewModelScope` resumes there.
❸ Until the backend exists, failures (DNS / 404 / connection reset) are caught and replaced with a friendly placeholder — the app stays usable for demo and integration testing.

### 6.6 `onCleared()` — lifecycle cleanup
```kotlin
override fun onCleared() { super.onCleared(); recorder.cancel() }
```
Prevents a stuck `MediaRecorder` if the screen is destroyed mid-recording.

---

## 7. UI — `ui/ChatScreen.kt`

A single Composable, three regions: **top bar**, **body** (welcome OR message list), **input bar**.

### 7.1 `ChatScreen` — top-level layout
- `Scaffold` with a custom `TopAppBar` (menu + "Agentic AI" + gradient avatar).
- Body switches between `WelcomeContent()` and `MessageList(messages)` based on whether any messages exist.
- `InputBar(...)` is pinned to the bottom and respects IME + navigation bar insets.
- `SnackbarHost` shows transient errors emitted via `state.errorMessage`.

### 7.2 `WelcomeContent()` — the empty state
Mirrors Gemini's empty screen:

```kotlin
val gradient = Brush.linearGradient(listOf(primary, secondary, tertiary))
Text(
    text = "Hello",
    fontSize = 56.sp,
    style = LocalTextStyle.current.copy(brush = gradient),           // gradient-painted text
)
Text("How can I help you today?", fontSize = 32.sp, …alpha 0.6f)
Row { SuggestionChip("…") ; SuggestionChip("…") }
```

The **gradient text** uses `TextStyle.brush` — the same trick Gemini uses for its hero greeting.

### 7.3 `MessageList(messages)` — auto-scrolling chat
```kotlin
LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
}
LazyColumn(state = listState, …) { items(messages, key = { it.id }) { MessageBubble(it) } }
```
Whenever a new message arrives the list animates to the bottom.

### 7.4 `MessageBubble(message)` — user vs assistant
- **User bubbles** are right-aligned with a primary-tinted background; the bottom-right corner is squared off (`bottomEnd = 4.dp`) — the Material talk-bubble convention.
- **Assistant bubbles** are left-aligned, paired with a small gradient ✦ avatar (`Icons.Filled.AutoAwesome`), and show a `CircularProgressIndicator` + "Thinking…" while `isLoading == true`.
- Voice messages get a `🎙` prefix so the user can tell text and voice prompts apart in the transcript.

### 7.5 `InputBar(...)` — the Gemini-style pill

```kotlin
Surface(shape = RoundedCornerShape(28.dp), color = surfaceVariant) {  // pill background
    BasicTextField(
        value = draft,
        onValueChange = onDraftChange,
        enabled = !isRecording,                                       // ❶ lock input during recording
        cursorBrush = SolidColor(primary),
        …
    )
}

IconButton(onClick = onMicTap, … background = if (isRecording) error else surfaceVariant) {
    Icon(if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic, …)   // ❷ toggle icon
}

if (draft.isNotBlank() && !isRecording) {
    IconButton(onClick = onSend, … background = primary) {
        if (isSending) CircularProgressIndicator(…) else Icon(Icons.AutoMirrored.Filled.Send, …)
    }
}
```

❶ While recording, the text box becomes a "Listening…" hint and is non-editable.
❷ The single round button on the right swaps between a 🎙 and a red ⏹ depending on `isRecording`.
The send arrow is **conditionally shown** — it only appears when there is something to send, identical to Gemini's behavior.

---

## 8. Hosting the UI — `MainActivity.kt`

```kotlin
class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()                         // ❶

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) chatViewModel.toggleRecording() }                    // ❷

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AgenticAITheme { ChatScreen(chatViewModel, onMicTap = ::handleMicTap) } }
    }

    private fun handleMicTap() {                                                     // ❸
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) chatViewModel.toggleRecording()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
```

❶ `by viewModels()` survives configuration changes — chat history is preserved on rotation.
❷ Modern Activity Result API for the runtime permission — no `onRequestPermissionsResult` boilerplate.
❸ Mic taps are **gated**: if the user has already granted `RECORD_AUDIO`, recording toggles immediately; otherwise the permission dialog is shown and recording starts on grant.

---

## 9. Theme — `ui/theme/`

`Theme.kt` defines an `AgenticAITheme` that:
1. Uses **Material You dynamic color** on Android 12+ (`dynamicLightColorScheme` / `dynamicDarkColorScheme`).
2. Falls back to a hand-tuned Gemini-like palette (blue / purple / pink) on older devices.
3. Honors the system's light/dark setting via `isSystemInDarkTheme()`.

This is what lets the gradient avatar, the hero "Hello" text, and the assistant bubble badge all share a cohesive color story.

---

## 10. Build Configuration Notes

`app/build.gradle.kts` highlights:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)        // Compose compiler plugin only
}
```

> **Why no `kotlin-android` plugin?**
> AGP 9.1+ ships with **built-in Kotlin support**. Declaring `org.jetbrains.kotlin.android` causes Gradle to fail with *"Cannot add extension with name 'kotlin'"*. We only declare the Compose compiler plugin, since it is not built in.

Other settings:

| Setting             | Value                       |
| ------------------- | --------------------------- |
| `compileSdk`        | 36 (minor 1)                |
| `minSdk` / `targetSdk` | 24 / 36                  |
| Source / target     | Java 17 (required by Compose 2024.12 BOM) |
| `buildFeatures.compose` | `true`                  |

---

## 11. End-to-End Flow Summary

1. User opens the app → `MainActivity` inflates `ChatScreen` inside `AgenticAITheme`.
2. **Text path**: user types in the pill → tap ✈️ → `sendText()` → user bubble + assistant loading bubble → Retrofit POST `{ "prompt": "…" }` → reply replaces the loading bubble.
3. **Voice path**: user taps 🎙 → runtime permission prompt (first time) → `AudioRecorder.start()` → user taps ⏹ → `AudioRecorder.stop()` returns Base64 → user bubble (🎙 Voice prompt) + assistant loading bubble → Retrofit POST `{ "audioBase64": "…" }` → reply replaces the loading bubble.
4. When the backend is ready, **only `ApiClient.BASE_URL` needs updating** — the rest of the pipeline is already wired.
