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
import kotlinx.coroutines.tasks.await

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class ChatViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _currentChatRoom = MutableStateFlow<ChatRoom?>(null)
    val currentChatRoom: StateFlow<ChatRoom?> = _currentChatRoom.asStateFlow()

    private val _smartReplies = MutableStateFlow<List<SmartReply>>(emptyList())
    val smartReplies: StateFlow<List<SmartReply>> = _smartReplies.asStateFlow()

    private val _isReceiverTyping = MutableStateFlow(false)
    val isReceiverTyping: StateFlow<Boolean> = _isReceiverTyping.asStateFlow()

    private val _receiverUser = MutableStateFlow<User?>(null)
    val receiverUser: StateFlow<User?> = _receiverUser.asStateFlow()

    private val _chatSummary = MutableStateFlow<String?>(null)
    val chatSummary: StateFlow<String?> = _chatSummary.asStateFlow()

    private val _messageTone = MutableStateFlow<String?>(null)
    val messageTone: StateFlow<String?> = _messageTone.asStateFlow()

    private val _emojiSuggestion = MutableStateFlow<String?>(null)
    val emojiSuggestion: StateFlow<String?> = _emojiSuggestion.asStateFlow()

    private val _translatedMessages = MutableStateFlow<Map<Int, String>>(emptyMap())
    val translatedMessages: StateFlow<Map<Int, String>> = _translatedMessages.asStateFlow()

    private var currentChatRoomId: String? = null
    private var messageListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null

    fun loadMessages(receiverId: String) {
        val senderId = auth.currentUser?.uid ?: return
        val chatRoomId = getChatRoomId(senderId, receiverId)
        
        if (currentChatRoomId == chatRoomId) return
        
        messageListener?.remove()
        typingListener?.remove()
        userListener?.remove()

        currentChatRoomId = chatRoomId

        userListener = firestore.collection("users").document(receiverId)
            .addSnapshotListener { snapshot, _ ->
                _receiverUser.value = snapshot?.toObject(User::class.java)
            }

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
                    
                    if (lastMsg.receiverId == senderId) {
                        generateSmartReplies(lastMsg.message)
                    } else {
                        _smartReplies.value = emptyList()
                    }
                }
            }

        
        typingListener = firestore.collection("chatRooms")
            .document(chatRoomId)
            .addSnapshotListener { snapshot, _ ->
                val room = snapshot?.toObject(ChatRoom::class.java)
                _currentChatRoom.value = room

                @Suppress("UNCHECKED_CAST")
                val typingMap = snapshot?.get("typing") as? Map<String, Boolean>
                _isReceiverTyping.value = typingMap?.get(receiverId) ?: false
            }
    }

    fun acceptChatRequest() {
        val roomId = currentChatRoomId ?: return
        firestore.collection("chatRooms").document(roomId)
            .update("status", "ACCEPTED")
    }

    fun sendMessage(receiverId: String, text: String, isBurn: Boolean = false) {
        val senderId = auth.currentUser?.uid ?: return
        val chatRoomId = currentChatRoomId ?: getChatRoomId(senderId, receiverId)

        viewModelScope.launch {
            try {
                _smartReplies.value = emptyList()
                setTypingStatus(false) 
                
                val userSnapshot = firestore.collection("users").document(senderId).get().await()
                val currentMood = userSnapshot.getString("mood") ?: "Neutral"

                val message = Message(
                    senderId = senderId,
                    receiverId = receiverId,
                    message = text,
                    timestamp = Timestamp.now(),
                    seen = false,
                    messageType = "TEXT",
                    senderMood = currentMood,
                    isBurn = isBurn
                )
                
                firestore.collection("chatRooms")
                    .document(chatRoomId)
                    .collection("messages")
                    .add(message)

                val roomUpdates = hashMapOf(
                    "lastMessage" to text,
                    "lastTimestamp" to Timestamp.now(),
                    "users" to listOf(senderId, receiverId),
                    "chatRoomId" to chatRoomId
                )

                if (_currentChatRoom.value == null) {
                    roomUpdates["status"] = "PENDING"
                    roomUpdates["requestedBy"] = senderId
                }
                
                firestore.collection("chatRooms")
                    .document(chatRoomId)
                    .set(roomUpdates, SetOptions.merge())

                val senderName = userSnapshot.getString("name") ?: "New Message"
                val notificationData = mapOf(
                    "lastMessage" to text,
                    "senderName" to senderName,
                    "senderId" to senderId,
                    "receiverId" to receiverId,
                    "timestamp" to Timestamp.now()
                )
                
                firestore.collection("chatRooms").document(chatRoomId)
                    .update("notificationTrigger", notificationData)
                    
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setTypingStatus(isTyping: Boolean, speed: String = "Normal") {
        val senderId = auth.currentUser?.uid ?: return
        val chatRoomId = currentChatRoomId ?: return
        
        firestore.collection("chatRooms").document(chatRoomId)
            .set(mapOf(
                "typing" to mapOf(senderId to isTyping),
                "typingSpeed" to mapOf(senderId to speed)
            ), SetOptions.merge())
    }

    fun sendPing(receiverId: String) {
        val senderId = auth.currentUser?.uid ?: return
        val chatRoomId = currentChatRoomId ?: getChatRoomId(senderId, receiverId)

        viewModelScope.launch {
            try {
                val userSnapshot = firestore.collection("users").document(senderId).get().await()
                val senderName = userSnapshot.getString("name") ?: "Friend"
                
                val notificationData = mapOf(
                    "lastMessage" to "⚡ Sent you a Morse Ping!",
                    "senderName" to senderName,
                    "senderId" to senderId,
                    "receiverId" to receiverId,
                    "timestamp" to Timestamp.now(),
                    "type" to "PING"
                )
                
                firestore.collection("chatRooms").document(chatRoomId)
                    .update("notificationTrigger", notificationData)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun summarizeChat() {
        val messagesList = _messages.value
        if (messagesList.isEmpty()) return

        viewModelScope.launch {
            try {
                val chatContent = messagesList.takeLast(50).joinToString("\n") { 
                    "${if (it.senderId == auth.currentUser?.uid) "Me" else "Friend"}: ${it.message}"
                }
                val prompt = "Summarize the following chat conversation in 3 short bullet points:\n\n$chatContent"
                val response = generativeModel.generateContent(prompt)
                _chatSummary.value = response.text
            } catch (e: Exception) {
                e.printStackTrace()
                _chatSummary.value = "Could not generate summary."
            }
        }
    }

    fun clearSummary() {
        _chatSummary.value = null
    }

    fun analyzeTone(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val prompt = "Analyze the emotional tone of this message in one or two words (e.g., Polite, Aggressive, Sarcastic, Joyful): \"$text\""
                val response = generativeModel.generateContent(prompt)
                _messageTone.value = response.text?.trim()
            } catch (e: Exception) {
                _messageTone.value = "Unknown"
            }
        }
    }

    fun clearTone() {
        _messageTone.value = null
    }

    fun suggestEmoji(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val prompt = "Suggest exactly one emoji that best fits the emotion of this message: \"$text\". Return only the emoji."
                val response = generativeModel.generateContent(prompt)
                _emojiSuggestion.value = response.text?.trim()
            } catch (e: Exception) {
                _emojiSuggestion.value = null
            }
        }
    }

    fun clearEmojiSuggestion() {
        _emojiSuggestion.value = null
    }

    fun translateMessage(message: String, position: Int) {
        val targetLangCode = _receiverUser.value?.preferredLanguage ?: "es"
        val targetLang = try {
            TranslateLanguage.fromLanguageTag(targetLangCode.lowercase()) ?: TranslateLanguage.SPANISH
        } catch (e: Exception) {
            TranslateLanguage.SPANISH
        }

        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(message)
            .addOnSuccessListener { languageCode ->
                val sourceLang = if (languageCode == "und") {
                    TranslateLanguage.ENGLISH
                } else {
                    TranslateLanguage.fromLanguageTag(languageCode) ?: TranslateLanguage.ENGLISH
                }

                if (sourceLang == targetLang) return@addOnSuccessListener

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang)
                    .setTargetLanguage(targetLang)
                    .build()
                val translator = Translation.getClient(options)

                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        translator.translate(message)
                            .addOnSuccessListener { translatedText ->
                                val currentMap = _translatedMessages.value.toMutableMap()
                                currentMap[position] = translatedText
                                _translatedMessages.value = currentMap
                            }
                    }
            }
    }

    private var lastRepliedMessage: String? = null

    private fun generateSmartReplies(lastMessage: String) {
        if (lastMessage == lastRepliedMessage || lastMessage.isBlank()) return
        
        viewModelScope.launch {
            try {
                lastRepliedMessage = lastMessage
                val prompt = "Based on this message: '$lastMessage', suggest 3 very short (max 3 words), natural, and helpful replies. Return only the replies separated by newlines, no numbers or bullets."
                val response = generativeModel.generateContent(prompt)
                val suggestions = response.text?.lines()
                    ?.filter { it.isNotBlank() }
                    ?.take(3)
                    ?.map { SmartReply(it.trim().replace(Regex("^[-*\\d.\\s]+"), "")) }
                    ?: emptyList()
                
                _smartReplies.value = suggestions
            } catch (e: Exception) {
                if (e.message?.contains("Quota") == true || e.message?.contains("429") == true) {
                    android.util.Log.w("ChatViewModel", "Smart replies quota reached (gemini-2.5-flash)")
                } else {
                    e.printStackTrace()
                }
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
        userListener?.remove()
    }
}
