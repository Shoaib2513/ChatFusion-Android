package com.example.chatfusion

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.chatfusion.databinding.ActivityChatBinding
import com.example.chatfusion.databinding.ItemSmartReplyBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()
    private var receiverId: String? = null
    private var receiverName: String? = null
    
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receiverId = intent.getStringExtra("receiverId")
        receiverName = intent.getStringExtra("receiverName")

        setupUI()
        observeViewModel()

        receiverId?.let { 
            viewModel.loadMessages(it)
            updateOnlineStatus(it)
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide default title to use custom view
        
        binding.tvToolbarName.text = receiverName ?: "Chat"
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            itemAnimator = null 
        }

        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val isEmpty = s.isNullOrBlank()
                binding.btnSend.animate()
                    .scaleX(if (isEmpty) 0.8f else 1.0f)
                    .scaleY(if (isEmpty) 0.8f else 1.0f)
                    .setDuration(200)
                    .start()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty() && receiverId != null) {
                viewModel.sendMessage(receiverId!!, text)
                binding.etMessage.text.clear()
                
                binding.btnSend.animate()
                    .rotation(360f)
                    .setDuration(300)
                    .withEndAction { binding.btnSend.rotation = 0f }
                    .start()
            }
        }
    }

    private fun updateOnlineStatus(receiverId: String) {
        firestore.collection("users").document(receiverId)
            .addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java) ?: return@addSnapshotListener
                
                // Update custom toolbar views
                binding.tvToolbarName.text = user.name
                binding.tvToolbarStatus.text = if (user.online) "Online" else "Offline"
                
                if (user.profileImageUrl.isNotEmpty()) {
                    binding.ivToolbarProfile.load(user.profileImageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_profile)
                        error(R.drawable.ic_profile)
                    }
                }

                binding.tvToolbarStatus.setTextColor(
                    if (user.online) ContextCompat.getColor(this, R.color.online_indicator)
                    else ContextCompat.getColor(this, R.color.text_secondary)
                )
            }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        chatAdapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                binding.rvMessages.smoothScrollToPosition(messages.size - 1)
                                markMessagesAsSeen(messages)
                            }
                        }
                    }
                }

                launch {
                    viewModel.smartReplies.collect { replies ->
                        updateSmartReplies(replies)
                    }
                }
            }
        }
    }

    private fun markMessagesAsSeen(messages: List<Message>) {
        val receiverId = receiverId ?: return
        val senderId = currentUserId ?: return
        val chatRoomId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"

        messages.forEach { message ->
            if (message.receiverId == senderId && !message.seen) {
                firestore.collection("chatRooms")
                    .document(chatRoomId)
                    .collection("messages")
                    .whereEqualTo("timestamp", message.timestamp)
                    .whereEqualTo("senderId", message.senderId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        for (doc in snapshot.documents) {
                            doc.reference.update("seen", true)
                        }
                    }
            }
        }
    }

    private fun updateSmartReplies(replies: List<SmartReply>) {
        if (replies.isEmpty()) {
            binding.scrollSmartReplies.visibility = View.GONE
            return
        }

        binding.chipGroupSmartReplies.removeAllViews()
        binding.scrollSmartReplies.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = 20f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator())
                .start()
        }

        replies.forEach { reply ->
            val chipBinding = ItemSmartReplyBinding.inflate(
                LayoutInflater.from(this),
                binding.chipGroupSmartReplies,
                false
            )
            chipBinding.chipSmartReply.text = reply.text
            chipBinding.chipSmartReply.setOnClickListener {
                receiverId?.let { id -> viewModel.sendMessage(id, reply.text) }
            }
            binding.chipGroupSmartReplies.addView(chipBinding.root)
        }
    }
}
