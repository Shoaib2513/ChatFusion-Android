package com.chatfusion.app

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
import com.chatfusion.app.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.ByteArrayOutputStream
import com.chatfusion.app.R

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var snapshotListener: ListenerRegistration? = null
    private var selectedImageUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
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
        snapshotListener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    binding.etEditName.setText(user?.name)
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
            val cleanBase64 = if (imageData.contains(",")) imageData.substringAfter(",") else imageData
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            binding.ivEditProfilePic.load(imageBytes) {
                crossfade(true)
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
                transformations(CircleCropTransformation())
            }
        } catch (e: Exception) {
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

        if (selectedImageUri != null) {
            val base64Image = encodeImageToBase64(selectedImageUri!!)
            if (base64Image != null) {
                updateFirestore(userId, mapOf("name" to newName, "profileImageUrl" to base64Image))
            } else {
                Toast.makeText(this, "Image processing failed", Toast.LENGTH_SHORT).show()
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
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 200, 200, true)
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun updateFirestore(userId: String, updates: Map<String, Any>) {
        firestore.collection("users").document(userId).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
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
