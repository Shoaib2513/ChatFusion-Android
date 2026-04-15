package com.chatfusion.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GeminiViewModel : ViewModel() {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private val chat = generativeModel.startChat(
        history = listOf(
            content(role = "user") { text("Hello, I am using ChatFusion. Please be my helpful AI assistant.") },
            content(role = "model") { text("Hi there! I'm your ChatFusion AI assistant. How can I help you today?") }
        )
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _smartReplies = MutableStateFlow<List<SmartReply>>(emptyList())
    val smartReplies: StateFlow<List<SmartReply>> = _smartReplies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        viewModelScope.launch {
            
            val userMsg = ChatMessage.User(userText)
            _messages.value = _messages.value + userMsg
            _smartReplies.value = emptyList()
            _isLoading.value = true

            try {
                
                val loadingAiMsg = ChatMessage.AI("", isLoading = true)
                _messages.value = _messages.value + loadingAiMsg

                val response = chat.sendMessage(userText)
                val aiText = response.text ?: "Sorry, I couldn't process that."

                
                _messages.value = _messages.value.dropLast(1) + ChatMessage.AI(aiText)
                
                generateSmartReplies(aiText)
            } catch (e: Exception) {
                Log.e("GeminiViewModel", "Chat message failed", e)
                val errorMessage = when {
                    e.message?.contains("Quota") == true || e.message?.contains("429") == true -> {
                        "You've reached the daily limit for gemini-2.5-flash. Please try again later."
                    }
                    e.message?.contains("503") == true || e.message?.contains("high demand") == true -> {
                        "AI is busy right now. Please try again soon."
                    }
                    else -> "Sorry, something went wrong. Please check your connection."
                }
                _messages.value = _messages.value.dropLast(1) + ChatMessage.AI(errorMessage)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun generateSmartReplies(lastAiResponse: String) {
        viewModelScope.launch {
            try {
                val prompt = "Based on this AI response: '$lastAiResponse', suggest 3 short, context-aware follow-up replies for the user. Provide only the replies separated by newlines."
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
}
