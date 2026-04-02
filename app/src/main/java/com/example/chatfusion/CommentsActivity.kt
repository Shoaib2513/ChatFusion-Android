package com.chatfusion.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatfusion.app.databinding.ActivityCommentsBinding
import com.chatfusion.app.ui.home.CommentAdapter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.chatfusion.app.R

class CommentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommentsBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var commentAdapter: CommentAdapter
    private var postId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        postId = intent.getStringExtra("POST_ID")

        Log.d("CommentsActivity", "Received POST_ID: $postId")

        if (postId == null) {
            Toast.makeText(this, "Post ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        loadComments()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        commentAdapter = CommentAdapter()
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(this@CommentsActivity)
            adapter = commentAdapter
        }

        binding.btnSendComment.setOnClickListener {
            val content = binding.etComment.text.toString().trim()
            if (content.isNotEmpty()) {
                sendComment(content)
            }
        }
    }

    private fun loadComments() {
        postId?.let { id ->
            Log.d("CommentsActivity", "Loading comments for postId: $id")
            firestore.collection("comments")
                .whereEqualTo("postId", id)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("CommentsActivity", "Error loading comments: ${e.message}", e)
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        return@addSnapshotListener
                    }
                    
                    val comments = snapshot?.toObjects(Comment::class.java) ?: emptyList()
                    Log.d("CommentsActivity", "Loaded ${comments.size} comments")
                    
                    commentAdapter.submitList(comments) {
                        if (comments.isNotEmpty()) {
                            binding.rvComments.scrollToPosition(comments.size - 1)
                        }
                    }
                }
        }
    }

    private fun sendComment(content: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        binding.btnSendComment.isEnabled = false
        
        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                val userName = user?.name ?: "Anonymous"
                val userProfileImage = user?.profileImageUrl ?: ""
                val commentId = firestore.collection("comments").document().id

                val comment = Comment(
                    commentId = commentId,
                    postId = postId!!,
                    userId = currentUserId,
                    userName = userName,
                    userProfileImage = userProfileImage,
                    content = content,
                    timestamp = Timestamp.now()
                )

                binding.etComment.setText("")

                firestore.runTransaction { transaction ->
                    val postRef = firestore.collection("posts").document(postId!!)
                    val commentRef = firestore.collection("comments").document(commentId)

                    transaction.set(commentRef, comment)
                    transaction.update(postRef, "commentsCount", FieldValue.increment(1))
                }.addOnSuccessListener {
                    Log.d("CommentsActivity", "Comment sent successfully")
                    binding.btnSendComment.isEnabled = true
                }.addOnFailureListener { e ->
                    Log.e("CommentsActivity", "Transaction failed", e)
                    Toast.makeText(this, "Failed to post comment", Toast.LENGTH_SHORT).show()
                    binding.btnSendComment.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                Log.e("CommentsActivity", "Failed to fetch user info", e)
                binding.btnSendComment.isEnabled = true
            }
    }
}
