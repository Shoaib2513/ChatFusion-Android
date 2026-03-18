package com.example.chatfusion

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val online: Boolean = false
)

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null,
    val seen: Boolean = false
)

data class ChatRoom(
    val lastMessage: String = "",
    val lastTimestamp: Timestamp? = null,
    val users: List<String> = emptyList(),
    val chatRoomId: String = ""
)
