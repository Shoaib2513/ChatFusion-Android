package com.example.chatfusion.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chatfusion.Post
import com.example.chatfusion.databinding.ItemPostBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

class PostAdapter : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PostViewHolder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: Post) {
            binding.tvAuthorName.text = post.userName
            binding.tvPostContent.text = post.content
            
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            binding.tvPostTime.text = post.timestamp?.toDate()?.let { sdf.format(it) } ?: ""

            binding.tvLikesCount.text = post.likes.size.toString()
            binding.tvCommentsCount.text = post.commentsCount.toString()

            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (post.likes.contains(currentUserId)) {
                binding.ivLike.setImageResource(android.R.drawable.btn_star_big_on) // Placeholder for filled heart
            } else {
                binding.ivLike.setImageResource(android.R.drawable.btn_star_big_off)
            }

            if (post.aiInsight.isNotEmpty()) {
                binding.cvAiInsight.visibility = View.VISIBLE
                binding.tvAiInsight.text = post.aiInsight
            } else {
                binding.cvAiInsight.visibility = View.GONE
            }
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem.postId == newItem.postId
        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem == newItem
    }
}
