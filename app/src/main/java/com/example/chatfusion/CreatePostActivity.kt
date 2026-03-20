package com.example.chatfusion

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.chatfusion.databinding.ActivityCreatePostBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var aiInsight: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

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

        binding.btnPost.setOnClickListener {
            val content = binding.etPostContent.text.toString()
            if (content.isEmpty()) {
                Toast.makeText(this, "Post cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            savePost(content)
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

    private fun savePost(content: String) {
        val userId = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName ?: "Anonymous"
        val postId = firestore.collection("posts").document().id

        val post = Post(
            postId = postId,
            userId = userId,
            userName = userName,
            content = content,
            timestamp = Timestamp.now(),
            aiInsight = aiInsight
        )

        binding.btnPost.isEnabled = false
        firestore.collection("posts").document(postId)
            .set(post)
            .addOnSuccessListener {
                Toast.makeText(this, "Post created!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                binding.btnPost.isEnabled = true
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
