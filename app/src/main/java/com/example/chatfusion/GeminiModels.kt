package com.chatfusion.app

sealed class ChatMessage {
    data class User(val text: String, val timestamp: Long = System.currentTimeMillis()) : ChatMessage()
    data class AI(val text: String, val timestamp: Long = System.currentTimeMillis(), val isLoading: Boolean = false) : ChatMessage()
}

data class SmartReply(val text: String)