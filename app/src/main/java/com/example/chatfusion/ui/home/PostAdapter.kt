package com.chatfusion.app.ui.home

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.chatfusion.app.Post
import com.chatfusion.app.R
import com.chatfusion.app.databinding.ItemPostBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class PostAdapter(
    private val onCommentClick: (Post) -> Unit
) : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding, onCommentClick)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PostViewHolder(
        private val binding: ItemPostBinding,
        private val onCommentClick: (Post) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val firestore = FirebaseFirestore.getInstance()
        private val auth = FirebaseAuth.getInstance()

        fun bind(post: Post) {
            val currentUserId = auth.currentUser?.uid ?: ""

            binding.tvAuthorName.text = post.userName
            binding.tvPostContent.text = post.content
            
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            binding.tvPostTime.text = post.timestamp?.toDate()?.let { sdf.format(it) } ?: ""

            binding.tvLikesCount.text = post.likes.size.toString()
            binding.tvCommentsCount.text = post.commentsCount.toString()

            // Handle Base64 Profile Image
            loadBase64Image(post.userProfileImage, binding.ivAuthorProfile, R.drawable.ic_user_placeholder)

            // Handle Base64 Post Image
            if (post.imageUrl.isNotEmpty()) {
                binding.ivPostImage.visibility = View.VISIBLE
                loadBase64Image(post.imageUrl, binding.ivPostImage, R.drawable.ic_placeholder)
            } else {
                binding.ivPostImage.visibility = View.GONE
            }

            // Like status
            val isLiked = post.likes.contains(currentUserId)
            if (isLiked) {
                binding.ivLike.setImageResource(R.drawable.ic_favorite_filled)
                binding.ivLike.setColorFilter(binding.root.context.getColor(R.color.colorPrimary))
            } else {
                binding.ivLike.setImageResource(R.drawable.ic_favorite)
                binding.ivLike.setColorFilter(binding.root.context.getColor(R.color.text_secondary))
            }

            binding.layoutLike.setOnClickListener {
                toggleLike(post, currentUserId, isLiked)
            }

            binding.layoutComment.setOnClickListener {
                onCommentClick(post)
            }

            if (post.aiInsight.isNotEmpty()) {
                binding.cvAiInsight.visibility = View.VISIBLE
                binding.tvAiInsight.text = post.aiInsight
            } else {
                binding.cvAiInsight.visibility = View.GONE
            }
        }

        private fun loadBase64Image(base64String: String, imageView: android.widget.ImageView, placeholder: Int) {
            if (base64String.isEmpty()) {
                imageView.setImageResource(placeholder)
                return
            }

            try {
                // Check if it's a URL or Base64
                if (base64String.startsWith("http")) {
                    imageView.load(base64String) {
                        placeholder(placeholder)
                        error(placeholder)
                    }
                } else {
                    val cleanBase64 = if (base64String.contains(",")) base64String.substringAfter(",") else base64String
                    val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    imageView.load(imageBytes) {
                        placeholder(placeholder)
                        error(placeholder)
                    }
                }
            } catch (e: Exception) {
                imageView.setImageResource(placeholder)
            }
        }

        private fun toggleLike(post: Post, userId: String, isLiked: Boolean) {
            val postRef = firestore.collection("posts").document(post.postId)
            if (isLiked) {
                postRef.update("likes", FieldValue.arrayRemove(userId))
            } else {
                postRef.update("likes", FieldValue.arrayUnion(userId))
            }
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem.postId == newItem.postId
        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem == newItem
    }
}
