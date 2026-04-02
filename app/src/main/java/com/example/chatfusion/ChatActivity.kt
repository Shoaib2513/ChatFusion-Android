package com.chatfusion.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
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
import coil.transform.CircleCropTransformation
import com.chatfusion.app.databinding.ActivityChatBinding
import com.chatfusion.app.databinding.ItemSmartReplyBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import com.chatfusion.app.R

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()
    private var receiverId: String? = null
    private var receiverName: String? = null
    
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var statusListener: ListenerRegistration? = null

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
            observeReceiverStatus(it)
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        binding.tvToolbarName.text = receiverName ?: "Chat"
        
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            // Disable blinking animation on update
            itemAnimator = null 
        }

        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val isEmpty = s.isNullOrBlank()
                binding.btnSend.animate()
                    .scaleX(if (isEmpty) 0.8f else 1.0f)
                    .scaleY(if (isEmpty) 0.8f else 1.0f)
                    .alpha(if (isEmpty) 0.6f else 1.0f)
                    .setDuration(150)
                    .start()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty() && receiverId != null) {
                viewModel.sendMessage(receiverId!!, text)
                binding.etMessage.text.clear()
            }
        }
    }

    private fun observeReceiverStatus(receiverId: String) {
        statusListener = firestore.collection("users").document(receiverId)
            .addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java) ?: return@addSnapshotListener
                
                binding.tvToolbarName.text = user.name
                binding.tvToolbarStatus.text = if (user.online) "Online" else "Offline"
                
                loadProfileImage(user.profileImageUrl)

                binding.tvToolbarStatus.setTextColor(
                    if (user.online) ContextCompat.getColor(this, R.color.online_indicator)
                    else ContextCompat.getColor(this, R.color.text_secondary)
                )
            }
    }

    private fun loadProfileImage(imageData: String) {
        if (imageData.isEmpty()) {
            binding.ivToolbarProfile.setImageResource(R.drawable.ic_profile)
            return
        }
        try {
            val cleanBase64 = if (imageData.contains(",")) imageData.substringAfter(",") else imageData
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            binding.ivToolbarProfile.load(imageBytes) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
            }
        } catch (e: Exception) {
            binding.ivToolbarProfile.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        chatAdapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                binding.rvMessages.scrollToPosition(messages.size - 1)
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

        messages.filter { it.receiverId == senderId && !it.seen }.forEach { message ->
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

    private fun updateSmartReplies(replies: List<SmartReply>) {
        if (replies.isEmpty()) {
            binding.scrollSmartReplies.visibility = View.GONE
            return
        }

        binding.chipGroupSmartReplies.removeAllViews()
        binding.scrollSmartReplies.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = 15f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
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
                receiverId?.let { id -> 
                    viewModel.sendMessage(id, reply.text)
                    binding.scrollSmartReplies.visibility = View.GONE
                }
            }
            binding.chipGroupSmartReplies.addView(chipBinding.root)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        statusListener?.remove()
    }
}
