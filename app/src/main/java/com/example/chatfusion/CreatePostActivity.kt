package com.example.chatfusion

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.chatfusion.databinding.ActivityCreatePostBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.util.UUID

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
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
        storage = FirebaseStorage.getInstance()

        setupUI()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        val currentUser = auth.currentUser
        binding.tvUsername.text = currentUser?.displayName ?: "User"

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
                uploadImageAndSavePost(content)
            } else {
                savePost(content, "")
            }
        }
    }

    private fun generateAIInsight(content: String) {
        binding.cvAiPreview.visibility = View.VISIBLE
        binding.tvAiPreviewText.text = "Analyzing your post..."
        binding.btnGenerateAi.isEnabled = false

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
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

    private fun uploadImageAndSavePost(content: String) {
        binding.btnPost.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        
        val imageName = UUID.randomUUID().toString()
        val imageRef = storage.reference.child("post_images/$imageName")

        selectedImageUri?.let { uri ->
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        savePost(content, downloadUrl.toString())
                    }
                }
                .addOnFailureListener {
                    binding.btnPost.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Image upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun savePost(content: String, imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName ?: "Anonymous"
        val userProfileImage = auth.currentUser?.photoUrl?.toString() ?: ""
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

        binding.btnPost.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

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
}
