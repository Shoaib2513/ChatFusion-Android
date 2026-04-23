package com.chatfusion.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import com.chatfusion.app.R
import com.chatfusion.app.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import android.widget.ImageView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            updateFcmToken()
        } else {
            Toast.makeText(this, "Notifications disabled. You won't receive chat alerts.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.navView.setupWithNavController(navController)
        
        checkNotificationPermission()
        setupNotificationListener()

        binding.navView.setOnLongClickListener {
            showBurningQRCode()
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_home || destination.id == R.id.navigation_discover ||
                destination.id == R.id.navigation_chats || destination.id == R.id.navigation_profile
            ) {
                binding.navView.visibility = View.VISIBLE
            } else {
                binding.navView.visibility = View.GONE
            }
        }
    }

    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM_TEST", "Token: $token")
                val uid = auth.currentUser?.uid
                if (uid != null && token != null) {
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                        .update("fcmToken", token)
                        .addOnSuccessListener { Log.d("FCM_TEST", "Token updated in Firestore") }
                }
            } else {
                Log.e("FCM_TEST", "Fetching FCM registration token failed", task.exception)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                updateFcmToken()
            }
        } else {
            updateFcmToken()
        }
    }

    private fun setupNotificationListener() {
        val uid = auth.currentUser?.uid ?: return
        
        
        FirebaseFirestore.getInstance().collection("chatRooms")
            .whereArrayContains("users", uid)
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    val trigger = change.document.get("notificationTrigger") as? Map<String, Any> ?: return@forEach
                    val receiverId = trigger["receiverId"] as? String
                    val senderId = trigger["senderId"] as? String ?: return@forEach
                    
                    
                    if (receiverId == uid && ChatFusionApp.currentChatId != senderId) {
                        val message = trigger["lastMessage"] as? String ?: "New message"
                        val senderName = trigger["senderName"] as? String ?: "Someone"
                        
                        showLocalNotification(senderName, message, senderId)
                        
                        
                        change.document.reference.update("notificationTrigger", null)
                    }
                }
            }
    }

    private fun showBurningQRCode() {
        val uid = auth.currentUser?.uid ?: return
        val timestamp = System.currentTimeMillis()
        val qrData = "chatfusion://add?uid=$uid&ts=$timestamp"
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }

        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            setPadding(40, 40, 40, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Burning QR Code")
            .setMessage("Ask your friend to scan this. Valid for 60 seconds.")
            .setView(imageView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showLocalNotification(title: String, message: String, senderId: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("receiverId", senderId)
            putExtra("receiverName", title)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "chat_notifications"
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Chat Messages",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
