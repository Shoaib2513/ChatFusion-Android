package com.chatfusion.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
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
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import com.chatfusion.app.R
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.common.util.concurrent.ListenableFuture

import java.text.SimpleDateFormat

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter { message, position ->
        viewModel.translateMessage(message.message, position)
    }
    private var receiverId: String? = null
    private var receiverName: String? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    private var statusListener: ListenerRegistration? = null

    private var lastShakeTime: Long = 0
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null

    private var batteryReceiver: BroadcastReceiver? = null
    private var noiseDetector: AmbientNoiseDetector? = null
    private var isBurnMode = false
    private var isFocusMode = false
    private var isGhostMode = false
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receiverId = intent.getStringExtra("receiverId")
        receiverName = intent.getStringExtra("receiverName")

        setupUI()
        setupClickListeners()
        setupShakeSensor()
        setupPrivacySensor()
        setupBatteryStatus()
        setupAmbientNoiseDetection()
        setupGhostMode()
        observeViewModel()

        receiverId?.let { viewModel.loadMessages(it) }
    }

    private fun setupUI() {
        binding.tvToolbarName.text = receiverName
        
        // Handle window insets to keep input above keyboard
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val keyboardVisible = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom > 0
            val imeHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
            
            // Apply padding to the root layout equal to the keyboard height
            binding.root.setPadding(0, 0, 0, imeHeight)
            
            if (keyboardVisible) {
                binding.rvMessages.postDelayed({
                    binding.rvMessages.smoothScrollToPosition(chatAdapter.itemCount)
                }, 100)
            }
            insets
        }

        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.adapter = chatAdapter

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.etMessage.addTextChangedListener(object : TextWatcher {
            private val typingHandler = Handler(Looper.getMainLooper())
            private var typingRunnable: Runnable? = null
            private var lastTypingTime = 0L
            private var charCount = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTypingTime > 2000) {
                    val speed = if (count > 5) "Fast" else "Normal"
                    receiverId?.let { viewModel.setTypingStatus(true, speed) }
                }
                lastTypingTime = currentTime

                typingRunnable?.let { typingHandler.removeCallbacks(it) }
                typingRunnable = Runnable {
                    receiverId?.let { viewModel.setTypingStatus(false, "None") }
                }
                typingHandler.postDelayed(typingRunnable!!, 3000)

                if (s.toString().isNotEmpty() && s.toString().length % 10 == 0) {
                    viewModel.suggestEmoji(s.toString())
                }
                
                if (s.toString().length > 20 && s.toString().endsWith(" ")) {
                    viewModel.analyzeTone(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupAmbientNoiseDetection() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            noiseDetector = AmbientNoiseDetector(this) { level ->
                if (level.contains("Loud") && isFocusMode) {
                    runOnUiThread {
                        Toast.makeText(this, "Environment is too noisy for Focus Mode!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            noiseDetector?.start()
        }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val msg = binding.etMessage.text.toString().trim()
            if (msg.isNotEmpty()) {
                viewModel.sendMessage(receiverId ?: "", msg, isBurnMode)
                binding.etMessage.setText("")
                if (isBurnMode) {
                    isBurnMode = false
                    binding.cardInput.setCardBackgroundColor(ContextCompat.getColor(this, R.color.msg_left_bg))
                }
            }
        }

        binding.btnSummarize.setOnClickListener { viewModel.summarizeChat() }
        binding.btnCloseSummary.setOnClickListener { viewModel.clearSummary() }
        binding.btnFocusMode.setOnClickListener { toggleFocusMode() }
        binding.btnGhostMode.setOnClickListener { toggleGhostMode() }
        binding.btnPing.setOnClickListener { sendMorsePing() }

        binding.btnAccept.setOnClickListener { viewModel.acceptChatRequest() }
        binding.btnIgnore.setOnClickListener { finish() }
    }

    private fun toggleFocusMode() {
        isFocusMode = !isFocusMode
        if (isFocusMode) {
            binding.btnFocusMode.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))
            binding.btnFocusMode.alpha = 1.0f
            Toast.makeText(this, "Focus Mode Enabled: Notifications Muted", Toast.LENGTH_SHORT).show()
        } else {
            binding.btnFocusMode.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnFocusMode.alpha = 0.6f
        }
    }

    private fun toggleEmojiPicker() {
    }

    private fun hideEmojiPicker() {
    }

    private fun observeReceiverStatus(id: String) {
        statusListener = firestore.collection("users").document(id)
            .addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java)
                user?.let {
                    binding.tvToolbarStatus.text = if (it.online) "Online" else "Offline"
                    loadProfileImage(it.profileImageUrl)
                }
            }
    }

    private fun formatLastSeen(date: Date): String {
        val diff = System.currentTimeMillis() - date.time
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
                                binding.rvMessages.smoothScrollToPosition(messages.size - 1)
                                markMessagesAsSeen(messages)
                                checkFlashAlert(messages.lastOrNull())
                            }
                        }
                    }
                }

                launch {
                    viewModel.smartReplies.collect { replies ->
                        updateSmartReplies(replies)
                    }
                }

                launch {
                    viewModel.isReceiverTyping.collect { isTyping ->
                        if (isTyping) {
                            val speed = viewModel.currentChatRoom.value?.typingSpeed?.get(receiverId) ?: "Normal"
                            binding.tvToolbarStatus.text = "Typing ($speed)..."
                            binding.tvToolbarStatus.setTextColor(ContextCompat.getColor(this@ChatActivity, R.color.colorPrimary))
                        } else {
                            receiverId?.let { id -> observeReceiverStatus(id) }
                        }
                    }
                }

                launch {
                    viewModel.currentChatRoom.collect { room ->
                        updateRequestUI(room)
                        checkBiometricLock(room)
                    }
                }

                launch {
                    viewModel.receiverUser.collect { user ->
                        user?.let {
                            binding.tvToolbarStatus.text = if (it.online) {
                                "Online • ${it.batteryLevel}% • ${it.connectionType}"
                            } else {
                                "Offline"
                            }
                        }
                    }
                }

                launch {
                    viewModel.chatSummary.collect { summary ->
                        if (summary != null) {
                            binding.tvSummaryContent.text = summary
                            binding.cardSummary.visibility = View.VISIBLE
                        } else {
                            binding.cardSummary.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.messageTone.collect { tone ->
                        tone?.let {
                            Toast.makeText(this@ChatActivity, "Message Tone: $it", Toast.LENGTH_SHORT).show()
                            viewModel.clearTone()
                        }
                    }
                }

                launch {
                    viewModel.emojiSuggestion.collect { emoji ->
                        emoji?.let {
                            binding.etMessage.append(" $it")
                            viewModel.clearEmojiSuggestion()
                        }
                    }
                }

                launch {
                    viewModel.translatedMessages.collect { translations ->
                        translations.forEach { (position, text) ->
                            Toast.makeText(this@ChatActivity, "Translated: $text", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateRequestUI(room: ChatRoom?) {
        val currentUser = currentUserId ?: return
        
        if (room == null || room.status == "ACCEPTED") {
            binding.layoutRequest.visibility = View.GONE
            binding.cardInput.visibility = View.VISIBLE
            binding.btnSend.visibility = View.VISIBLE
            return
        }

        if (room.status == "PENDING") {
            binding.layoutRequest.visibility = View.VISIBLE
            binding.cardInput.visibility = View.GONE
            binding.btnSend.visibility = View.GONE

            if (room.requestedBy == currentUser) {
                binding.tvRequestMessage.text = "Waiting for $receiverName to accept your request..."
                binding.layoutRequestButtons.visibility = View.GONE
            } else {
                binding.tvRequestMessage.text = "$receiverName wants to message you"
                binding.layoutRequestButtons.visibility = View.VISIBLE
            }
        }
    }

    private var hasAuthenticated = false

    private fun checkBiometricLock(room: ChatRoom?) {
        if (room?.isLocked == true && !hasAuthenticated) {
            binding.viewPrivacyOverlay.visibility = View.VISIBLE
            showBiometricPrompt()
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    hasAuthenticated = true
                    binding.viewPrivacyOverlay.visibility = View.GONE
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Chat Locked")
            .setSubtitle("Use biometric to unlock this chat")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun setupShakeSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        sensorManager?.registerListener(object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val x = event?.values?.get(0) ?: 0f
                val y = event?.values?.get(1) ?: 0f
                val z = event?.values?.get(2) ?: 0f
                
                val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble())
                if (acceleration > 20) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastShakeTime > 2000) {
                        lastShakeTime = currentTime
                        isBurnMode = !isBurnMode
                        if (isBurnMode) {
                            binding.cardInput.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
                            Toast.makeText(this@ChatActivity, "Burn Mode Enabled 🔥", Toast.LENGTH_SHORT).show()
                        } else {
                            binding.cardInput.setCardBackgroundColor(ContextCompat.getColor(this@ChatActivity, R.color.msg_left_bg))
                            Toast.makeText(this@ChatActivity, "Burn Mode Disabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun setupPrivacySensor() {
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        sensorManager?.registerListener(object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val distance = event?.values?.get(0) ?: 0f
                if (distance < (proximitySensor?.maximumRange ?: 0f)) {
                    binding.viewPrivacyOverlay.visibility = View.VISIBLE
                } else if (!hasAuthenticated || viewModel.currentChatRoom.value?.isLocked != true) {
                    binding.viewPrivacyOverlay.visibility = View.GONE
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun setupBatteryStatus() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = level * 100 / scale.toFloat()
                
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                
                val chargeType = if (isCharging) "Charging" else "Unplugged"
                
                currentUserId?.let { uid ->
                    firestore.collection("users").document(uid)
                        .update("batteryLevel", batteryPct.toInt(), "connectionType", chargeType)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        this.registerReceiver(batteryReceiver, filter)
    }

    private fun sendMorsePing() {
        receiverId?.let { id ->
            viewModel.sendPing(id)
            Toast.makeText(this, "Morse Ping Sent! ⚡", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerSOS() {
        // Implementation for SOS
    }

    private fun markMessagesAsSeen(messages: List<Message>) {
        val senderId = receiverId ?: return
        val currentUid = currentUserId ?: return
        val roomId = if (currentUid < senderId) "${currentUid}_${senderId}" else "${senderId}_${currentUid}"

        messages.forEach { message ->
            if (message.senderId == senderId && !message.seen) {
                firestore.collection("chatRooms").document(roomId)
                    .collection("messages")
                    .whereEqualTo("timestamp", message.timestamp)
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
        binding.scrollSmartReplies.visibility = View.GONE
        if (replies.isEmpty()) return

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
            val chipBinding = ItemSmartReplyBinding.inflate(layoutInflater, binding.chipGroupSmartReplies, false)
            chipBinding.chipSmartReply.text = reply.text
            chipBinding.chipSmartReply.setOnClickListener {
                binding.etMessage.setText(reply.text)
                binding.btnSend.performClick()
            }
            binding.chipGroupSmartReplies.addView(chipBinding.root)
        }
    }

    private fun setupGhostMode() {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun toggleGhostMode() {
        isGhostMode = !isGhostMode
        if (isGhostMode) {
            startFaceDetection()
            binding.btnGhostMode.setAlpha(1.0f)
            binding.btnGhostMode.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))
            Toast.makeText(this, "Ghost Mode Enabled", Toast.LENGTH_SHORT).show()
            
            // Show stealth indicator
            binding.tvToolbarStatus.text = "👻 Ghost Mode Active"
            binding.tvToolbarStatus.setTextColor(Color.MAGENTA)
        } else {
            stopFaceDetection()
            binding.btnGhostMode.setAlpha(0.6f)
            binding.btnGhostMode.clearColorFilter()
            binding.viewPrivacyOverlay.visibility = View.GONE
            Toast.makeText(this, "Ghost Mode Disabled", Toast.LENGTH_SHORT).show()
            
            // Restore status
            receiverId?.let { id -> observeReceiverStatus(id) }
            binding.tvToolbarStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun startFaceDetection() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.size > 1) {
                                runOnUiThread {
                                    binding.viewPrivacyOverlay.visibility = View.VISIBLE
                                }
                            } else {
                                runOnUiThread {
                                    if (binding.viewPrivacyOverlay.visibility == View.VISIBLE && !isFocusMode) {
                                         binding.viewPrivacyOverlay.visibility = View.GONE
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopFaceDetection() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProviderFuture.get().unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        ChatFusionApp.currentChatId = receiverId
    }

    private fun checkFlashAlert(message: Message?) {
        if (message != null && message.senderId != currentUserId && message.message.startsWith("URGENT:", ignoreCase = true)) {
            pulseFlashlight()
        }
    }

    private fun pulseFlashlight() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = cameraManager.cameraIdList[0]
        
        lifecycleScope.launch {
            repeat(3) {
                cameraManager.setTorchMode(cameraId, true)
                kotlinx.coroutines.delay(200)
                cameraManager.setTorchMode(cameraId, false)
                kotlinx.coroutines.delay(200)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ChatFusionApp.currentChatId = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        statusListener?.remove()
        noiseDetector?.stop()
        if (batteryReceiver != null) {
            this.unregisterReceiver(batteryReceiver)
        }
    }
}
