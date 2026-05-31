package com.example.agenticaiapp.network

import retrofit2.http.Body
import retrofit2.http.POST

interface AgentApi {
    @POST("agent")
    suspend fun sendPrompt(@Body request: AgentRequest): AgentResponse
}
