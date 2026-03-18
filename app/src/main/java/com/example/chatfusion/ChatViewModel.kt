package com.example.chatfusion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _smartReplies = MutableStateFlow<List<SmartReply>>(emptyList())
    val smartReplies: StateFlow<List<SmartReply>> = _smartReplies.asStateFlow()

    private var currentChatRoomId: String? = null

    fun loadMessages(receiverId: String) {
        val senderId = auth.currentUser?.uid ?: return
        currentChatRoomId = getChatRoomId(senderId, receiverId)

        firestore.collection("chatRooms")
            .document(currentChatRoomId!!)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val messagesList = snapshot?.toObjects(Message::class.java) ?: emptyList()
                _messages.value = messagesList

                // Generate smart replies based on the last received message
                if (messagesList.isNotEmpty()) {
                    val lastMsg = messagesList.last()
                    if (lastMsg.senderId == receiverId) {
                        generateSmartReplies(lastMsg.message)
                    } else {
                        _smartReplies.value = emptyList()
                    }
                }
            }
    }

    fun sendMessage(receiverId: String, text: String) {
        val senderId = auth.currentUser?.uid ?: return
        val chatRoomId = currentChatRoomId ?: getChatRoomId(senderId, receiverId)

        val message = Message(
            senderId = senderId,
            receiverId = receiverId,
            message = text,
            timestamp = Timestamp.now()
        )

        viewModelScope.launch {
            try {
                firestore.collection("chatRooms")
                    .document(chatRoomId)
                    .collection("messages")
                    .add(message)

                // Update last message in ChatRoom
                val chatRoom = ChatRoom(
                    lastMessage = text,
                    lastTimestamp = Timestamp.now(),
                    users = listOf(senderId, receiverId),
                    chatRoomId = chatRoomId
                )
                firestore.collection("chatRooms").document(chatRoomId).set(chatRoom)
                
                _smartReplies.value = emptyList()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun generateSmartReplies(lastMessage: String) {
        viewModelScope.launch {
            try {
                val prompt = "The user received this message: '$lastMessage'. Suggest 3 short, helpful, context-aware smart replies. Provide only the replies separated by newlines."
                val response = generativeModel.generateContent(prompt)
                val suggestions = response.text?.lines()
                    ?.filter { it.isNotBlank() }
                    ?.take(3)
                    ?.map { SmartReply(it.trim().removePrefix("- ").removePrefix("1. ").removePrefix("2. ").removePrefix("3. ")) }
                    ?: emptyList()
                
                _smartReplies.value = suggestions
            } catch (e: Exception) {
                _smartReplies.value = emptyList()
            }
        }
    }

    private fun getChatRoomId(user1: String, user2: String): String {
        return if (user1 < user2) "${user1}_${user2}" else "${user2}_${user1}"
    }
}
