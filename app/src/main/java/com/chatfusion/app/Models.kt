package com.chatfusion.app

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val name: String = "",
    val nameLower: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val online: Boolean = false,
    val lastSeen: Timestamp? = null,
    val bio: String = "",
    val mood: String = "Neutral",
    val batteryLevel: Int = -1,
    val connectionType: String = "Unknown",
    val ghostMode: Boolean = false,
    val preferredLanguage: String = "SPANISH",
    val fcmToken: String = "",
    val followers: List<String> = emptyList(),
    val following: List<String> = emptyList()
)

@Keep
@IgnoreExtraProperties
data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null,
    val seen: Boolean = false,
    val messageType: String = "TEXT", 
    val mediaUrl: String = "",
    val senderMood: String = "Neutral",
    val isBurn: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    val chatRoomId: String = "",
    val users: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTimestamp: Timestamp? = null,
    val typing: Map<String, Boolean>? = null,
    val typingSpeed: Map<String, String>? = null,
    val notificationTrigger: Map<String, Any>? = null,
    val status: String = "", // PENDING, ACCEPTED, REJECTED, or empty
    val requestedBy: String = "",
    val isLocked: Boolean = false,
    val screenshotProtected: Boolean = false
)

@Keep
@IgnoreExtraProperties
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

@Keep
@IgnoreExtraProperties
data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfileImage: String = "",
    val content: String = "",
    val timestamp: Timestamp? = null
)
