package com.chatfusion.app

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
        binding.toolbar.setNavigationOnClickListener { finish() }

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
                binding.tvAiPreviewText.text = "Could not generate insight: ${e.message}"
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

            // Calculate scaled dimensions to stay under Firestore document limit (1MB)
            // Aim for roughly 600px width/height
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
            // Compress with lower quality to save space
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
