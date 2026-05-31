package com.example.agenticaiapp.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class AgentRequest(
    val prompt: String? = null,
    val audioBase64: String? = null,
)

@JsonClass(generateAdapter = false)
data class AgentResponse(
    val reply: String,
)
