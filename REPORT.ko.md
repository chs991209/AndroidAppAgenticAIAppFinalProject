# AgenticAIApp — 코드 워크스루

사용자의 **텍스트 프롬프트**와 **음성 프롬프트**를 수집해 백엔드 에이전트로 전송하는 Gemini 스타일의 Android 클라이언트입니다. 백엔드는 아직 구현되지 않았으며, 네트워크 계층은 갖춰져 있고 UI는 placeholder 응답으로 자연스럽게 폴백하여 전체 플로우를 end-to-end로 검증할 수 있도록 설계되었습니다.

---

## 1. 아키텍처 개요

```
app/src/main/java/com/example/agenticaiapp/
├── MainActivity.kt              # Compose 호스팅 + 런타임 마이크 권한 처리
├── audio/
│   └── AudioRecorder.kt         # MediaRecorder → m4a → Base64
├── network/
│   ├── AgentApi.kt              # Retrofit 인터페이스 (POST /agent)
│   ├── ApiClient.kt             # Retrofit + OkHttp + Moshi 싱글톤
│   └── Models.kt                # AgentRequest, AgentResponse
└── ui/
    ├── ChatModels.kt            # ChatMessage, ChatUiState 정의
    ├── ChatViewModel.kt         # 상태 보유, 전송/녹음 오케스트레이션
    ├── ChatScreen.kt            # Compose UI (상단 바 / 리스트 / 입력 바)
    └── theme/
        ├── Color.kt
        └── Theme.kt
```

**기술 스택**

| 계층     | 라이브러리                                              |
| -------- | ------------------------------------------------------- |
| UI       | Jetpack Compose, Material 3, Material Icons Extended    |
| 상태     | `AndroidViewModel`, `StateFlow`, Kotlin Coroutines      |
| 오디오   | `android.media.MediaRecorder` + `android.util.Base64`   |
| 네트워크 | Retrofit 2, OkHttp logging interceptor, Moshi (Kotlin)  |
| 빌드     | AGP 9.1.1 (built-in Kotlin), Gradle 9.3.1, Java 17      |

**데이터 흐름**

```
사용자 입력 ──▶ ChatViewModel.sendText() ──┐
                                            ▼
사용자 마이크 ──▶ AudioRecorder.stop()       sendToBackend()
                   (Base64 반환)             ──▶ ApiClient.agentApi.sendPrompt(AgentRequest)
                                                    │
                                                    ▼
                                             AgentResponse.reply ─▶ UI 버블
```

---

## 2. 매니페스트 및 권한

`app/src/main/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

- `INTERNET`은 Retrofit 호출을 위해 필요합니다.
- `RECORD_AUDIO`는 **런타임** 권한(위험 권한)이므로, 사용자가 마이크 버튼을 처음 누를 때 요청합니다 — `MainActivity.handleMicTap()` 참고.

---

## 3. 음성 녹음 — `audio/AudioRecorder.kt`

`MediaRecorder`를 감싸 AAC-in-MP4(`.m4a`) 파일을 생성하고, 그것을 전송 가능한 **Base64 문자열**로 반환하는 작은 래퍼입니다.

### 3.1 `start()` — 녹음 시작

```kotlin
fun start() {
    if (recorder != null) return                                     // ❶ 중복 호출 방지

    val file = File(context.cacheDir, "voice_prompt_${System.currentTimeMillis()}.m4a")
    outputFile = file

    val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)                                       // ❷ API 31+ 부터는 Context 필수
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

❶ 멱등성(idempotent) — 이미 녹음 중일 때 `start()`를 다시 호출해도 아무 일도 일어나지 않습니다.
❷ Android 12(API 31)부터는 `MediaRecorder(Context)` 생성자가 의무화되었습니다.
❸ AAC@128 kbps, 44.1 kHz는 파일 크기가 작고, 호환성이 넓으며, STT/에이전트 입력으로 충분한 품질입니다.

### 3.2 `stop()` — 녹음 종료 및 Base64 반환

```kotlin
fun stop(): String? {
    val current = recorder ?: return null
    return try {
        current.stop()
        current.release()
        recorder = null
        val file = outputFile ?: return null
        val bytes = file.readBytes()
        Base64.encodeToString(bytes, Base64.NO_WRAP)                 // ❶ 줄바꿈 없는 Base64
    } catch (t: Throwable) {
        current.runCatching { release() }
        recorder = null
        null
    } finally {
        outputFile?.delete()                                         // ❷ 임시 파일은 반드시 정리
        outputFile = null
    }
}
```

❶ `Base64.NO_WRAP`은 `\n` 없는 한 줄짜리 문자열을 만들어 JSON 필드에 바로 넣을 수 있습니다.
❷ `stop()`이 예외를 던지더라도 캐시 파일은 항상 정리됩니다.

### 3.3 `cancel()` — 전송 없이 폐기
녹음 중에 화면이 파괴되는 상황에서 사용됩니다(`ChatViewModel.onCleared()`). 레코더를 해제하고 임시 파일을 삭제합니다.

---

## 4. 네트워크 계층 — `network/`

### 4.1 요청·응답 모델 — `Models.kt`

```kotlin
data class AgentRequest(
    val prompt: String? = null,
    val audioBase64: String? = null,
)

data class AgentResponse(
    val reply: String,
)
```

백엔드는 명세 그대로의 페이로드를 받습니다:

```json
{ "prompt": "오늘 날씨 어때?" }
```

또는 음성의 경우:

```json
{ "audioBase64": "AAAAGGZ0eXBtcDQy…" }
```

### 4.2 Retrofit 인터페이스 — `AgentApi.kt`

```kotlin
interface AgentApi {
    @POST("agent")
    suspend fun sendPrompt(@Body request: AgentRequest): AgentResponse
}
```

`suspend` 덕분에 ViewModel이 콜백 없이 코루틴 안에서 응답을 `await`할 수 있습니다.

### 4.3 Retrofit 클라이언트 — `ApiClient.kt`

```kotlin
private const val BASE_URL = "https://example.com/api/"   // ← 백엔드가 준비되면 교체

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

- 싱글톤(`object ApiClient`) — 프로세스 전체에서 OkHttp 커넥션 풀을 하나만 사용.
- read/write 타임아웃은 LLM 추론을 고려해 넉넉히 설정.
- 인터셉터로 개발 중 요청·응답 본문을 전부 로그로 남김.
- 백엔드가 준비되면 **`BASE_URL`만 바꾸면 됩니다.**

---

## 5. 상태 모델 — `ui/ChatModels.kt`

```kotlin
enum class MessageRole { USER, ASSISTANT }
enum class MessageKind { TEXT, VOICE }

data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
    val kind: MessageKind = MessageKind.TEXT,
    val isLoading: Boolean = false,        // 응답 대기 중인 어시스턴트 placeholder
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val isRecording: Boolean = false,
    val errorMessage: String? = null,
)
```

단일 `ChatUiState`를 `StateFlow<ChatUiState>`로 노출합니다 — 이 값에 의존하는 Compose 컴포저블은 자동으로 재구성(recompose)됩니다.

---

## 6. ViewModel — `ui/ChatViewModel.kt`

### 6.1 `onDraftChange(value)` — 입력 필드 갱신
```kotlin
fun onDraftChange(value: String) { _state.update { it.copy(draft = value) } }
```

### 6.2 `sendText()` — 텍스트 프롬프트 POST
```kotlin
fun sendText() {
    val draft = _state.value.draft.trim()
    if (draft.isEmpty() || _state.value.isSending) return            // ❶ 가드
    _state.update { it.copy(draft = "") }                            // ❷ 입력창 낙관적 초기화
    appendUserMessage(draft, MessageKind.TEXT)                       // ❸ 사용자 버블 즉시 표시
    sendToBackend(prompt = draft, audioBase64 = null)                // ❹ 요청 발사
}
```

### 6.3 `toggleRecording()` — 단일 마이크 버튼
```kotlin
fun toggleRecording() {
    if (_state.value.isRecording) stopRecording() else startRecording()
}
```

마이크 버튼은 **토글형**입니다: 첫 탭이 녹음을 시작, 두 번째 탭이 녹음을 멈추고 전송합니다. Gemini의 음성 버튼 사용감을 그대로 재현했습니다.

### 6.4 `startRecording()` / `stopRecording()`
```kotlin
private fun stopRecording() {
    val base64 = recorder.stop()
    _state.update { it.copy(isRecording = false) }
    if (base64.isNullOrEmpty()) {
        _state.update { it.copy(errorMessage = "녹음이 비어 있습니다.") }
        return
    }
    appendUserMessage("Voice prompt", MessageKind.VOICE)
    sendToBackend(prompt = null, audioBase64 = base64)               // ❶ audioBase64 POST
}
```

❶ 녹음이 끝나면 동일한 엔드포인트로 인코딩된 오디오를 전송합니다 — 요청 필드만 달라집니다.

### 6.5 `sendToBackend(prompt, audioBase64)` — 핵심 호출

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
                "(백엔드 미연결) ${if (audioBase64 != null) "음성" else "텍스트"} 프롬프트를 수신했습니다."
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

❶ **어시스턴트 placeholder 버블**(`isLoading = true`)을 즉시 추가하여 "생각 중…" 스피너가 바로 보이게 합니다.
❷ 네트워크 호출은 `Dispatchers.IO`에서 수행하고, `viewModelScope`가 메인 스레드로 재개해 주므로 UI 업데이트는 자동으로 메인 스레드에서 일어납니다.
❸ 백엔드가 존재하지 않는 동안 발생하는 실패(DNS / 404 / connection reset)는 모두 잡아내고, 사용자 친화적인 placeholder 문구로 대체합니다 — 데모와 통합 테스트 중에도 앱은 계속 사용 가능합니다.

### 6.6 `onCleared()` — 라이프사이클 정리
```kotlin
override fun onCleared() { super.onCleared(); recorder.cancel() }
```
녹음 중 화면이 파괴되어 `MediaRecorder`가 멈춰 있는 상황을 방지합니다.

---

## 7. UI — `ui/ChatScreen.kt`

단일 Composable, 세 가지 영역으로 구성: **상단 바**, **본문**(웰컴 화면 또는 메시지 목록), **입력 바**.

### 7.1 `ChatScreen` — 최상위 레이아웃
- 커스텀 `TopAppBar`(메뉴 + "Agentic AI" + 그라데이션 아바타)를 가진 `Scaffold`.
- 메시지 유무에 따라 본문이 `WelcomeContent()`와 `MessageList(messages)` 사이에서 전환됩니다.
- `InputBar(...)`는 하단에 고정되며 IME(키보드)와 내비게이션 바 inset을 존중합니다.
- `SnackbarHost`가 `state.errorMessage`로 방출된 일시적 오류 메시지를 보여줍니다.

### 7.2 `WelcomeContent()` — 비어 있는 상태
Gemini의 첫 화면을 그대로 재현:

```kotlin
val gradient = Brush.linearGradient(listOf(primary, secondary, tertiary))
Text(
    text = "Hello",
    fontSize = 56.sp,
    style = LocalTextStyle.current.copy(brush = gradient),           // 그라데이션 텍스트
)
Text("How can I help you today?", fontSize = 32.sp, …alpha 0.6f)
Row { SuggestionChip("…") ; SuggestionChip("…") }
```

**그라데이션 텍스트**는 `TextStyle.brush`를 사용합니다 — Gemini가 인사말에 쓰는 것과 같은 기법입니다.

### 7.3 `MessageList(messages)` — 자동 스크롤 채팅
```kotlin
LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
}
LazyColumn(state = listState, …) { items(messages, key = { it.id }) { MessageBubble(it) } }
```
새 메시지가 도착할 때마다 리스트가 하단으로 부드럽게 스크롤됩니다.

### 7.4 `MessageBubble(message)` — 사용자 vs 어시스턴트
- **사용자 버블**은 우측 정렬, primary 색을 입힌 배경, 우측 하단 모서리만 각져 있음(`bottomEnd = 4.dp`) — Material의 말풍선 컨벤션입니다.
- **어시스턴트 버블**은 좌측 정렬, 작은 그라데이션 ✦ 아바타(`Icons.Filled.AutoAwesome`)와 함께 표시되며, `isLoading == true`인 동안 `CircularProgressIndicator`와 "생각 중…" 문구가 보입니다.
- 음성 메시지에는 `🎙` 접두사가 붙어, 사용자가 대화 기록에서 텍스트/음성 프롬프트를 구분할 수 있게 합니다.

### 7.5 `InputBar(...)` — Gemini 스타일 알약(pill) 입력 바

```kotlin
Surface(shape = RoundedCornerShape(28.dp), color = surfaceVariant) {  // pill 배경
    BasicTextField(
        value = draft,
        onValueChange = onDraftChange,
        enabled = !isRecording,                                       // ❶ 녹음 중에는 입력 잠금
        cursorBrush = SolidColor(primary),
        …
    )
}

IconButton(onClick = onMicTap, … background = if (isRecording) error else surfaceVariant) {
    Icon(if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic, …)   // ❷ 아이콘 토글
}

if (draft.isNotBlank() && !isRecording) {
    IconButton(onClick = onSend, … background = primary) {
        if (isSending) CircularProgressIndicator(…) else Icon(Icons.AutoMirrored.Filled.Send, …)
    }
}
```

❶ 녹음 중에는 텍스트 박스의 placeholder가 "Listening…"으로 바뀌고, 편집이 불가능해집니다.
❷ 오른쪽의 둥근 버튼 하나가 `isRecording` 값에 따라 🎙와 빨간 ⏹ 사이를 전환합니다.
전송 화살표는 **조건부 표시** — 보낼 내용이 있을 때만 나타나며, Gemini와 동일한 동작입니다.

---

## 8. UI 호스팅 — `MainActivity.kt`

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

❶ `by viewModels()`로 생성한 ViewModel은 구성 변경(예: 회전)에도 살아남아 채팅 기록이 보존됩니다.
❷ 모던한 Activity Result API를 사용한 런타임 권한 처리 — `onRequestPermissionsResult` 보일러플레이트가 필요 없습니다.
❸ 마이크 탭은 **권한 게이트**를 통과해야 합니다: 사용자가 이미 `RECORD_AUDIO`를 허용한 경우 즉시 녹음을 토글하고, 그렇지 않으면 권한 다이얼로그를 띄운 뒤 허용 시점에 녹음을 시작합니다.

---

## 9. 테마 — `ui/theme/`

`Theme.kt`는 `AgenticAITheme`를 정의하며 다음을 수행합니다:
1. Android 12+에서 **Material You 다이내믹 컬러**(`dynamicLightColorScheme` / `dynamicDarkColorScheme`)를 사용.
2. 그 이하 버전에서는 직접 튜닝한 Gemini 풍의 팔레트(파랑 / 보라 / 핑크)로 폴백.
3. `isSystemInDarkTheme()`로 시스템의 라이트/다크 설정을 따름.

이 덕분에 그라데이션 아바타, 히어로 "Hello" 텍스트, 어시스턴트 버블 배지가 모두 일관된 컬러 스토리를 공유합니다.

---

## 10. 빌드 구성 노트

`app/build.gradle.kts` 주요 부분:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)        // Compose 컴파일러 플러그인만 선언
}
```

> **왜 `kotlin-android` 플러그인을 선언하지 않는가?**
> AGP 9.1+에는 **내장 Kotlin 지원**이 포함되어 있습니다. `org.jetbrains.kotlin.android`를 함께 선언하면 Gradle이 *"Cannot add extension with name 'kotlin'"* 오류로 실패합니다. 내장되어 있지 않은 Compose 컴파일러 플러그인만 별도로 선언합니다.

기타 설정:

| 설정                    | 값                          |
| ----------------------- | --------------------------- |
| `compileSdk`            | 36 (minor 1)                |
| `minSdk` / `targetSdk`  | 24 / 36                     |
| Source / target         | Java 17 (Compose 2024.12 BOM 요구사항) |
| `buildFeatures.compose` | `true`                      |

---

## 11. End-to-End 플로우 요약

1. 사용자가 앱을 실행 → `MainActivity`가 `AgenticAITheme` 안에서 `ChatScreen`을 인플레이트.
2. **텍스트 경로**: 사용자가 알약 입력 바에 입력 → ✈️ 탭 → `sendText()` → 사용자 버블 + 어시스턴트 로딩 버블 → Retrofit이 `{ "prompt": "…" }`로 POST → 응답이 로딩 버블을 대체.
3. **음성 경로**: 사용자가 🎙 탭 → (최초 1회) 런타임 권한 요청 → `AudioRecorder.start()` → 사용자가 ⏹ 탭 → `AudioRecorder.stop()`이 Base64 반환 → 사용자 버블(🎙 Voice prompt) + 어시스턴트 로딩 버블 → Retrofit이 `{ "audioBase64": "…" }`로 POST → 응답이 로딩 버블을 대체.
4. 백엔드가 준비되면, **`ApiClient.BASE_URL`만 갱신**하면 끝 — 나머지 파이프라인은 이미 연결되어 있습니다.
