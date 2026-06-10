package com.example.agenticaiapp.ui

/**
 * Action the agent decides to perform locally on the device.
 *
 * In the real product the backend would return this kind of structured
 * instruction. Until the backend is wired up, [ActionRouter] performs a
 * very small on-device classification so the YouTube playback flow can
 * be demoed end-to-end.
 */
sealed interface AgentAction {
    /** No client-side action — fall through to the normal backend call. */
    object None : AgentAction

    /** Open the YouTube app on the given URL. */
    data class PlayYouTube(val url: String) : AgentAction
}

/**
 * One-shot UI events emitted from [ChatViewModel] for the Activity to consume.
 * Modeled as events (not state) so they fire exactly once even across
 * configuration changes.
 */
sealed interface ChatEvent {
    data class OpenYouTube(val url: String) : ChatEvent
}

/**
 * Local intent classifier — stub for the future backend-driven router.
 *
 * Stub behavior: every prompt (text or voice) is treated as the command
 * "play IVE music video". No keyword filtering — the agent always plays
 * the hard-coded demo URL. When the real backend is wired up, replace
 * these methods with the action payload returned by the API.
 */
object ActionRouter {

    /** The only video this stub will ever play. */
    const val STUB_VIDEO_URL =
        "https://www.youtube.com/watch?v=B1ShLiq3EVc&pp=ygUPaXZlIG11c2ljIHZpZGVv"

    fun classifyText(prompt: String): AgentAction = AgentAction.PlayYouTube(STUB_VIDEO_URL)

    fun classifyVoice(): AgentAction = AgentAction.PlayYouTube(STUB_VIDEO_URL)
}

/**
 * Metadata used to render the in-chat video info widget.
 *
 * In production the backend will return this payload alongside the
 * PlayYouTube action; for now it is provided by [StubVideoCatalog].
 */
data class VideoCardData(
    val videoId: String,
    val title: String,
    val channel: String,
    val viewCount: String,     // pre-formatted, e.g. "42.6M views"
    val uploadedTime: String,  // pre-formatted, e.g. "9 months ago"
    val thumbnailUrl: String,
    val videoUrl: String,
    val description: String,   // long-form text streamed into the assistant bubble
)

/**
 * Hard-coded video metadata for the stub. Replaces the backend's
 * "video info" response until the real service is wired up.
 *
 * The thumbnail URL uses YouTube's freely-served static thumbnail
 * endpoint (`img.youtube.com/vi/{id}/hqdefault.jpg`) — the same one
 * used by Notion / KakaoTalk-style link previews. No API key required.
 */
object StubVideoCatalog {

    private val iveCard = VideoCardData(
        videoId = "B1ShLiq3EVc",
        title = "IVE 아이브 'XOXZ' MV",
        channel = "STARSHIP",
        viewCount = "42.6M views",     // 실제 유튜브 조회수 기반 업데이트
        uploadedTime = "9 months ago", // 발매일(25년 8월) 기준 최신화
        thumbnailUrl = "https://img.youtube.com/vi/B1ShLiq3EVc/hqdefault.jpg",
        videoUrl = ActionRouter.STUB_VIDEO_URL,
        description = """
            조회수 42,655,168회  2025. 8. 25.  #IVE #アイヴ #아이브
             STARSHIP SQUARE (Domestic) : https://bit.ly/3HtQ7GF
            STARSHIP SQUARE (Global) : https://bit.ly/4i5Dl1g
            IVE US EXCLUSIVE STORE : https://shop.ive-starship.com/

            IVE X (Twitter)
            :   / ivestarship
            :   / ive_twt
            :   / ivestarship_jp
            IVE Instagram :   / ivestarship
            IVE Facebook: https://fb.me/IVEstarship
            IVE Berriz: https://berriz.in/ko/IVE
            IVE TikTok:   / ive.official
            IVE Weibo: https://weibo.com/ivestarship

            #IVE #아이브 #アイヴ
        """.trimIndent(),
    )

    fun cardFor(@Suppress("UNUSED_PARAMETER") url: String): VideoCardData = iveCard
}