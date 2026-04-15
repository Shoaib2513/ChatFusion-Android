package com.chatfusion.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.emoji2.emojipicker.EmojiPickerView
import android.widget.FrameLayout
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.UUID
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.chatfusion.app.R

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()
    private var receiverId: String? = null
    private var receiverName: String? = null
    
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://chatfusion-3adc6.firebasestorage.app")
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var statusListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receiverId = intent.getStringExtra("receiverId")
        receiverName = intent.getStringExtra("receiverName")

        setupUI()
        observeViewModel()

        receiverId?.let { 
            if (it.isNotEmpty()) {
                viewModel.loadMessages(it)
                observeReceiverStatus(it)
            }
        }
    }

    private fun setupUI() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                if (ime.bottom > 0) ime.bottom else systemBars.bottom
            )
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        binding.tvToolbarName.text = receiverName ?: "Chat"
        
    binding.toolbar.setNavigationOnClickListener { finish() }

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
                    .alpha(if (isEmpty) 0.6f else 1.0f)
                    .setDuration(150)
                    .start()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty() && !receiverId.isNullOrEmpty()) {
                viewModel.sendMessage(receiverId!!, text)
                binding.etMessage.text.clear()
            }
        }

        binding.etMessage.setOnClickListener {
            hideEmojiPicker()
        }

        binding.btnEmoji.setOnClickListener {
            toggleEmojiPicker()
        }

        binding.btnGif.setOnClickListener {
            showGifPicker()
        }

        setupRichContentReceiver()
    }

    private fun setupRichContentReceiver() {
        val receiver = OnReceiveContentListener { _, payload ->
            val split = payload.partition { item -> item.uri != null }
            val uriContent = split.first
            val remaining = split.second

            if (uriContent != null) {
                val clipData = uriContent.clip
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    uploadAndSendImage(uri)
                }
            }
            remaining
        }

        ViewCompat.setOnReceiveContentListener(
            binding.etMessage,
            arrayOf("image/*", "image/gif"),
            receiver
        )
    }

    private fun uploadAndSendImage(uri: Uri) {
        val rid = receiverId ?: return
        if (rid.isEmpty()) return

        val isGif = contentResolver.getType(uri)?.contains("gif", ignoreCase = true) == true ||
                uri.toString().contains("gif", ignoreCase = true)

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        if (isGif) {
                            inputStream.readBytes()
                        } else {
                            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return@use null
                            val maxDimension = 1280f
                            val width = originalBitmap.width
                            val height = originalBitmap.height
                            
                            val scale = if (width > height) {
                                if (width > maxDimension) maxDimension / width else 1f
                            } else {
                                if (height > maxDimension) maxDimension / height else 1f
                            }
                            
                            val finalBitmap = if (scale < 1f) {
                                Bitmap.createScaledBitmap(
                                    originalBitmap,
                                    (width * scale).toInt(),
                                    (height * scale).toInt(),
                                    true
                                )
                            } else {
                                originalBitmap
                            }

                            val outputStream = ByteArrayOutputStream()
                            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                            val data = outputStream.toByteArray()
                            
                            if (finalBitmap != originalBitmap) {
                                finalBitmap.recycle()
                            }
                            originalBitmap.recycle()
                            
                            data
                        }
                    }
                }

                if (bytes == null || bytes.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ChatActivity, "Failed to process image", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val fileName = UUID.randomUUID().toString() + if (isGif) ".gif" else ".jpg"
                val ref = storage.reference.child("chat_media/$fileName")
                
                val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType(if (isGif) "image/gif" else "image/jpeg")
                    .build()

                ref.putBytes(bytes, metadata).await()
                
                val downloadUri = ref.downloadUrl.await()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    val type = if (isGif) "GIF" else "IMAGE"
                    viewModel.sendMessage(rid, "", type, downloadUri.toString())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    android.util.Log.e("StorageException", "Upload failed", e)
                    
                    val message = when {
                        e.message?.contains("Object does not exist") == true ->
                            "Storage error: Object not found. Please check your Firebase Storage configuration."
                        e.message?.contains("terminated") == true ->
                            "Upload interrupted. Please try again."
                        else -> "Failed to upload image: ${e.localizedMessage}"
                    }
                    Toast.makeText(this@ChatActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleEmojiPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val emojiPicker = findViewById<EmojiPickerView>(R.id.emoji_picker)
        
        if (emojiPicker != null && emojiPicker.visibility == View.VISIBLE) {
            // If picker is visible, hide it and show keyboard
            emojiPicker.visibility = View.GONE
            binding.etMessage.requestFocus()
            imm.showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT)
        } else {
            // Hide keyboard first
            imm.hideSoftInputFromWindow(binding.etMessage.windowToken, 0)
            
            // Show or create picker after a small delay to allow keyboard to hide
            binding.etMessage.postDelayed({
                if (emojiPicker != null) {
                    emojiPicker.visibility = View.VISIBLE
                } else {
                    val picker = EmojiPickerView(this).apply {
                        id = R.id.emoji_picker
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            (resources.displayMetrics.heightPixels * 0.35).toInt()
                        )
                        setOnEmojiPickedListener { emojiItem ->
                            binding.etMessage.append(emojiItem.emoji)
                        }
                    }
                    binding.layoutBottom.addView(picker)
                }
            }, 100)
        }
    }

    private fun hideEmojiPicker() {
        findViewById<EmojiPickerView>(R.id.emoji_picker)?.visibility = View.GONE
    }

    private fun showGifPicker() {
        val gifs = listOf(
            "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExNHJndXIzYXQ3Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3ZSZW5kZXJfZmlsZQ/3o7TKMGpxP5OqP8L2E/giphy.gif",
            "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExNHJndXIzYXQ3Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3R4Z3ZSZW5kZXJfZmlsZQ/l0HlHFRbmaZtBRhXG/giphy.gif"
        )
        receiverId?.let { 
            viewModel.sendMessage(it, "", "GIF", gifs[0])
        }
    }

    private fun observeReceiverStatus(receiverId: String) {
        if (receiverId.isEmpty()) return
        
        statusListener = firestore.collection("users").document(receiverId)
            .addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java) ?: return@addSnapshotListener
                
                binding.tvToolbarName.text = user.name
                
                if (user.online) {
                    binding.tvToolbarStatus.text = "Online"
                    binding.tvToolbarStatus.setTextColor(ContextCompat.getColor(this, R.color.online_indicator))
                } else {
                    val lastSeenTime = user.lastSeen?.toDate()
                    binding.tvToolbarStatus.text = if (lastSeenTime != null) {
                        "Last seen ${formatLastSeen(lastSeenTime)}"
                    } else {
                        "Offline"
                    }
                    binding.tvToolbarStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
                
                loadProfileImage(user.profileImageUrl)
            }
    }

    private fun formatLastSeen(date: Date): String {
        val now = Date()
        val diff = now.time - date.time
        
        return when {
            diff < 60_000 -> "just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(date)
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
        if (receiverId.isEmpty()) return
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
                    if (id.isNotEmpty()) {
                        viewModel.sendMessage(id, reply.text)
                        binding.scrollSmartReplies.visibility = View.GONE
                    }
                }
            }
            binding.chipGroupSmartReplies.addView(chipBinding.root)
        }
    }

    override fun onResume() {
        super.onResume()
        ChatFusionApp.currentChatId = receiverId
    }

    override fun onPause() {
        super.onPause()
        ChatFusionApp.currentChatId = null
    }

    override fun onDestroy() {
        super.onDestroy()
        statusListener?.remove()
    }
}
