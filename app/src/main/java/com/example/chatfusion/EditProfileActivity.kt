package com.example.chatfusion

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.chatfusion.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.ivEditProfilePic.load(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        
        // LOG THIS: It will show you exactly what bucket your app is looking for
        val bucketName = storage.reference.bucket
        Log.d("EditProfileActivity", "Current Bucket Name: '$bucketName'")
        
        if (bucketName.isEmpty()) {
            Toast.makeText(this, "CRITICAL: No Storage Bucket found in google-services.json", Toast.LENGTH_LONG).show()
        }

        setupToolbar()
        loadCurrentUserData()

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

    private fun loadCurrentUserData() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                binding.etEditName.setText(user?.name)
                if (!user?.profileImageUrl.isNullOrEmpty()) {
                    binding.ivEditProfilePic.load(user?.profileImageUrl) {
                        placeholder(R.drawable.ic_profile)
                        error(R.drawable.ic_profile)
                    }
                }
            }
    }

    private fun saveProfile() {
        val newName = binding.etEditName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.etEditName.error = "Name cannot be empty"
            return
        }

        val userId = auth.currentUser?.uid ?: return
        
        if (storage.reference.bucket.isEmpty()) {
            Toast.makeText(this, "Bucket missing. Check Firebase Console.", Toast.LENGTH_LONG).show()
            return
        }

        binding.btnSaveProfile.isEnabled = false
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()
        
        if (selectedImageUri != null) {
            uploadImageAndSaveProfile(userId, newName)
        } else {
            updateFirestore(userId, mapOf("name" to newName))
        }
    }

    private fun uploadImageAndSaveProfile(userId: String, name: String) {
        val fileName = "profile_${System.currentTimeMillis()}.jpg"
        val ref = storage.reference.child("profiles/$userId/$fileName")

        // Using simple putFile without complex chains to isolate the error
        ref.putFile(selectedImageUri!!)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    updateFirestore(userId, mapOf(
                        "name" to name,
                        "profileImageUrl" to uri.toString()
                    ))
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditProfileActivity", "UPLOAD FAILED", e)
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnSaveProfile.isEnabled = true
            }
    }

    private fun updateFirestore(userId: String, updates: Map<String, Any>) {
        firestore.collection("users").document(userId).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.btnSaveProfile.isEnabled = true
            }
    }
}
