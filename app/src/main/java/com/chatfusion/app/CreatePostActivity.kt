package com.chatfusion.app

import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.core.net.toUri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.chatfusion.app.databinding.ActivityCreatePostBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.chatfusion.app.R

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var aiInsight: String = ""
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            binding.ivPostImage.visibility = View.VISIBLE
            binding.ivPostImage.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupUI()
        loadCurrentUserProfile()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { handleBackNavigation() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        binding.btnGenerateAi.setOnClickListener {
            val content = binding.etPostContent.text.toString()
            if (content.length < 10) {
                Toast.makeText(this, "Please write a longer post for AI insight!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generateAIInsight(content)
        }

        binding.btnAddImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnPost.setOnClickListener {
            val content = binding.etPostContent.text.toString()
            if (content.isEmpty() && selectedImageUri == null) {
                Toast.makeText(this, "Post cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedImageUri != null) {
                processImageAndSavePost(content)
            } else {
                savePost(content, "")
            }
        }

        binding.btnSaveDraft.setOnClickListener {
            saveDraft()
        }
        
        checkAndLoadDraft()
    }

    private fun saveDraft() {
        val content = binding.etPostContent.text.toString()
        val sharedPrefs = getSharedPreferences("post_drafts", MODE_PRIVATE)
        sharedPrefs.edit {
            putString("draft_content", content)
            putString("draft_image_uri", selectedImageUri?.toString())
        }
        Toast.makeText(this, "Draft saved!", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndLoadDraft() {
        val sharedPrefs = getSharedPreferences("post_drafts", MODE_PRIVATE)
        val draftContent = sharedPrefs.getString("draft_content", "")
        val draftImageUri = sharedPrefs.getString("draft_image_uri", null)

        if (!draftContent.isNullOrEmpty() || draftImageUri != null) {
            
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Restore Draft?")
                .setMessage("You have an unsaved draft. Would you like to restore it?")
                .setPositiveButton("Restore") { _, _ ->
                    binding.etPostContent.setText(draftContent)
                    draftImageUri?.let {
                        selectedImageUri = it.toUri()
                        binding.ivPostImage.visibility = View.VISIBLE
                        binding.ivPostImage.setImageURI(selectedImageUri)
                    }
                }
                .setNegativeButton("Discard") { _, _ ->
                    clearDraft()
                }
                .show()
        }
    }

    private fun clearDraft() {
        val sharedPrefs = getSharedPreferences("post_drafts", MODE_PRIVATE)
        sharedPrefs.edit { clear() }
    }

    private fun handleBackNavigation() {
        val content = binding.etPostContent.text.toString()
        if (content.isNotEmpty() || selectedImageUri != null) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Save draft?")
                .setMessage("You can save this post as a draft and finish it later.")
                .setPositiveButton("Save Draft") { _, _ ->
                    saveDraft()
                    finish()
                }
                .setNegativeButton("Discard") { _, _ ->
                    clearDraft()
                    finish()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            finish()
        }
    }

    private fun loadCurrentUserProfile() {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                if (user != null) {
                    binding.tvUsername.text = user.name
                    loadProfileImage(user.profileImageUrl)
                }
            }
    }

    private fun loadProfileImage(imageData: String) {
        if (imageData.isEmpty()) {
            binding.ivUserAvatar.setImageResource(R.drawable.ic_profile)
            return
        }
        try {
            val cleanBase64 = if (imageData.contains(",")) imageData.substringAfter(",") else imageData
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            binding.ivUserAvatar.load(imageBytes) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
            }
        } catch (e: Exception) {
            binding.ivUserAvatar.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun generateAIInsight(content: String) {
        binding.cvAiPreview.visibility = View.VISIBLE
        binding.tvAiPreviewText.text = "Analyzing your post..."
        binding.btnGenerateAi.isEnabled = false

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )

        lifecycleScope.launch {
            try {
                val prompt = "Provide a very brief, 1-sentence social media insight or summary for this post: $content"
                val response = generativeModel.generateContent(prompt)
                aiInsight = response.text ?: ""
                binding.tvAiPreviewText.text = aiInsight
            } catch (e: Exception) {
                Log.e("CreatePostActivity", "AI Insight failed", e)
                val errorMessage = when {
                    e.message?.contains("503") == true || e.message?.contains("high demand") == true -> {
                        "AI service is currently busy due to high demand. Please try again in a few moments."
                    }
                    e.message?.contains("429") == true -> {
                        "Too many requests. Please wait a moment before trying again."
                    }
                    else -> "Could not generate insight. Please check your connection and try again."
                }
                binding.tvAiPreviewText.text = errorMessage
            } finally {
                binding.btnGenerateAi.isEnabled = true
            }
        }
    }

    private fun processImageAndSavePost(content: String) {
        val uri = selectedImageUri ?: return
        
        binding.btnPost.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val base64Image = withContext(Dispatchers.IO) {
                    encodeImageToBase64(uri)
                }
                
                if (base64Image == null) {
                    handleUploadError(Exception("Could not process image"))
                    return@launch
                }

                savePost(content, base64Image)
            } catch (e: Exception) {
                handleUploadError(e)
            }
        }
    }

    private suspend fun encodeImageToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            
            
            val scale = 600f / Math.max(originalBitmap.width, originalBitmap.height).coerceAtLeast(1)
            val scaledBitmap = if (scale < 1) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (originalBitmap.width * scale).toInt(),
                    (originalBitmap.height * scale).toInt(),
                    true
                )
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e("ImageProcess", "Error encoding image", e)
            null
        }
    }

    private fun handleUploadError(e: Exception) {
        binding.btnPost.isEnabled = true
        binding.progressBar.visibility = View.GONE
        e.printStackTrace()
        Toast.makeText(this, "Process failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }

    private fun savePost(content: String, imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return
        
        binding.btnPost.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                val userName = user?.name ?: "Anonymous"
                val userProfileImage = user?.profileImageUrl ?: ""
                val postId = firestore.collection("posts").document().id

                val post = Post(
                    postId = postId,
                    userId = userId,
                    userName = userName,
                    userProfileImage = userProfileImage,
                    content = content,
                    imageUrl = imageUrl,
                    timestamp = Timestamp.now(),
                    aiInsight = aiInsight
                )

                firestore.collection("posts").document(postId)
                    .set(post)
                    .addOnSuccessListener {
                        clearDraft()
                        Toast.makeText(this, "Post created!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener {
                        binding.btnPost.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                binding.btnPost.isEnabled = true
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Could not fetch user info", Toast.LENGTH_SHORT).show()
            }
    }
}
