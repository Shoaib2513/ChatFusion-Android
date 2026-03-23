package com.example.chatfusion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.example.chatfusion.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.ByteArrayOutputStream

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var snapshotListener: ListenerRegistration? = null
    private var selectedImageUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            // Load selected local image immediately for preview
            binding.ivEditProfilePic.load(it) {
                transformations(CircleCropTransformation())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupToolbar()
        observeUserData()

        binding.ivEditProfilePic.setOnClickListener {
            getContent.launch("image/*")
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun observeUserData() {
        val userId = auth.currentUser?.uid ?: return
        // Use SnapshotListener for real-time updates on this screen too
        snapshotListener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("EditProfile", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    binding.etEditName.setText(user?.name)
                    // Only load from network/DB if user hasn't just picked a new local image
                    if (selectedImageUri == null) {
                        loadProfileImage(user?.profileImageUrl)
                    }
                }
            }
    }

    private fun loadProfileImage(imageData: String?) {
        if (imageData.isNullOrEmpty()) {
            binding.ivEditProfilePic.setImageResource(R.drawable.ic_profile)
            return
        }
        
        try {
            if (imageData.startsWith("data:image")) {
                binding.ivEditProfilePic.load(imageData) {
                    crossfade(true)
                    placeholder(R.drawable.ic_profile)
                    error(R.drawable.ic_profile)
                    transformations(CircleCropTransformation())
                }
            } else {
                val imageBytes = Base64.decode(imageData, Base64.DEFAULT)
                binding.ivEditProfilePic.load(imageBytes) {
                    crossfade(true)
                    placeholder(R.drawable.ic_profile)
                    error(R.drawable.ic_profile)
                    transformations(CircleCropTransformation())
                }
            }
        } catch (e: Exception) {
            Log.e("EditProfile", "Error loading image", e)
            binding.ivEditProfilePic.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun saveProfile() {
        val newName = binding.etEditName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.etEditName.error = "Name cannot be empty"
            return
        }

        val userId = auth.currentUser?.uid ?: return
        binding.btnSaveProfile.isEnabled = false
        Toast.makeText(this, "Saving changes...", Toast.LENGTH_SHORT).show()

        if (selectedImageUri != null) {
            val base64Image = encodeImageToBase64(selectedImageUri!!)
            if (base64Image != null) {
                updateFirestore(userId, mapOf(
                    "name" to newName,
                    "profileImageUrl" to base64Image
                ))
            } else {
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
                binding.btnSaveProfile.isEnabled = true
            }
        } else {
            updateFirestore(userId, mapOf("name" to newName))
        }
    }

    private fun encodeImageToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            
            // Resize to 250x250 for better quality while staying under 1MB Firestore limit
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 250, 250, true)
            
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            
            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    private fun updateFirestore(userId: String, updates: Map<String, Any>) {
        firestore.collection("users").document(userId).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                selectedImageUri = null // Reset selection after success
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
                binding.btnSaveProfile.isEnabled = true
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }
}
