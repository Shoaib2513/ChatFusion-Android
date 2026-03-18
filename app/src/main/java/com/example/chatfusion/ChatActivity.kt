package com.example.chatfusion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatfusion.databinding.ActivityChatBinding
import com.example.chatfusion.databinding.ItemSmartReplyBinding
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()
    private var receiverId: String? = null
    private var receiverName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receiverId = intent.getStringExtra("receiverId")
        receiverName = intent.getStringExtra("receiverName")

        setupUI()
        observeViewModel()

        receiverId?.let { viewModel.loadMessages(it) }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = receiverName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty() && receiverId != null) {
                viewModel.sendMessage(receiverId!!, text)
                binding.etMessage.text.clear()
            }
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

    private fun updateSmartReplies(replies: List<SmartReply>) {
        binding.chipGroupSmartReplies.removeAllViews()
        if (replies.isEmpty()) {
            binding.scrollSmartReplies.visibility = View.GONE
        } else {
            binding.scrollSmartReplies.visibility = View.VISIBLE
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
}
