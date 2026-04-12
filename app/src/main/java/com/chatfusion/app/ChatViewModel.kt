package com.chatfusion.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _smartReplies = MutableStateFlow<List<SmartReply>>(emptyList())
    val smartReplies: StateFlow<List<SmartReply>> = _smartReplies.asStateFlow()

    private val _isReceiverTyping = MutableStateFlow(false)
    val isReceiverTyping: StateFlow<Boolean> = _isReceiverTyping.asStateFlow()

    private var currentChatRoomId: String? = null
    private var messageListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null

    fun loadMessages(receiverId: String) {
        val senderId = auth.currentUser?.uid ?: return
        val chatRoomId = getChatRoomId(senderId, receiverId)
        
        if (currentChatRoomId == chatRoomId) return
        
        messageListener?.remove()
        typingListener?.remove()
        currentChatRoomId = chatRoomId

        messageListener = firestore.collection("chatRooms")
            .document(chatRoomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val messagesList = snapshot?.documents?.mapNotNull { it.toObject(Message::class.java) } ?: emptyList()
                _messages.value = messagesList

                if (messagesList.isNotEmpty()) {
                    val lastMsg = messagesList.last()
                    // Generate replies if the last message is from the other person
                    if (lastMsg.receiverId == senderId) {
                        generateSmartReplies(lastMsg.message)
                    } else {
                        _smartReplies.value = emptyList()
                    }
                }
            }

        // Listen for receiver's typing status
        typingListener = firestore.collection("chatRooms")
            .document(chatRoomId)
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val typingMap = snapshot?.get("typing") as? Map<String, Boolean>
                _isReceiverTyping.value = typingMap?.get(receiverId) ?: false
            }
    }

    fun sendMessage(receiverId: String, text: String) {
        val senderId = auth.currentUser?.uid ?: return
        val chatRoomId = currentChatRoomId ?: getChatRoomId(senderId, receiverId)

        val message = Message(
            senderId = senderId,
            receiverId = receiverId,
            message = text,
            timestamp = Timestamp.now(),
            seen = false
        )

        viewModelScope.launch {
            try {
                _smartReplies.value = emptyList()
                setTypingStatus(false) // Stop typing when sending
                
                // 1. Add message to the sub-collection
                firestore.collection("chatRooms")
                    .document(chatRoomId)
                    .collection("messages")
                    .add(message)

                // 2. Update/Create the chatRoom document with merge to avoid rule issues
                val roomUpdates = hashMapOf(
                    "lastMessage" to text,
                    "lastTimestamp" to Timestamp.now(),
                    "users" to listOf(senderId, receiverId),
                    "chatRoomId" to chatRoomId
                )
                
                firestore.collection("chatRooms")
                    .document(chatRoomId)
                    .set(roomUpdates, SetOptions.merge())
                    
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setTypingStatus(isTyping: Boolean) {
        val senderId = auth.currentUser?.uid ?: return
        val chatRoomId = currentChatRoomId ?: return
        
        firestore.collection("chatRooms").document(chatRoomId)
            .set(mapOf("typing" to mapOf(senderId to isTyping)), SetOptions.merge())
    }

    private fun generateSmartReplies(lastMessage: String) {
        viewModelScope.launch {
            try {
                val prompt = "Based on this message: '$lastMessage', suggest 3 very short (max 3 words), natural, and helpful replies. Return only the replies separated by newlines, no numbers or bullets."
                val response = generativeModel.generateContent(prompt)
                val suggestions = response.text?.lines()
                    ?.filter { it.isNotBlank() }
                    ?.take(3)
                    ?.map { SmartReply(it.trim().replace(Regex("^[-*\\d.\\s]+"), "")) }
                    ?: emptyList()
                
                _smartReplies.value = suggestions
            } catch (e: Exception) {
                e.printStackTrace()
                _smartReplies.value = emptyList()
            }
        }
    }

    private fun getChatRoomId(user1: String, user2: String): String {
        return if (user1 < user2) "${user1}_${user2}" else "${user2}_${user1}"
    }

    override fun onCleared() {
        super.onCleared()
        messageListener?.remove()
        typingListener?.remove()
    }
}
