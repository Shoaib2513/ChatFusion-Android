package com.chatfusion.app

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val name: String = "",
    val nameLower: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val online: Boolean = false,
    val lastSeen: Timestamp? = null,
    val bio: String = "",
    val followers: List<String> = emptyList(),
    val following: List<String> = emptyList()
)

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null,
    val seen: Boolean = false
)

data class ChatRoom(
    val chatRoomId: String = "",
    val users: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTimestamp: Timestamp? = null
)

data class Post(
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfileImage: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val timestamp: Timestamp? = null,
    val likes: List<String> = emptyList(),
    val commentsCount: Int = 0,
    val aiInsight: String = ""
)

data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfileImage: String = "",
    val content: String = "",
    val timestamp: Timestamp? = null
)
