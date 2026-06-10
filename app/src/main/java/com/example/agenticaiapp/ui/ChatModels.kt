package com.example.agenticaiapp.ui

enum class MessageRole { USER, ASSISTANT }

enum class MessageKind { TEXT, VOICE }

data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
    val kind: MessageKind = MessageKind.TEXT,
    val isLoading: Boolean = false,
    val videoCard: VideoCardData? = null,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val isRecording: Boolean = false,
    val errorMessage: String? = null,
)
