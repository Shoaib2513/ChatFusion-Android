package com.example.chatfusion.ui.home

import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.chatfusion.Comment
import com.example.chatfusion.R
import com.example.chatfusion.databinding.ItemCommentBinding

class CommentAdapter : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: Comment) {
            binding.tvCommenterName.text = comment.userName
            binding.tvCommentContent.text = comment.content
            
            if (comment.userProfileImage.isNotEmpty()) {
                try {
                    val imageData = comment.userProfileImage
                    val cleanBase64 = if (imageData.contains(",")) imageData.substringAfter(",") else imageData
                    val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    binding.ivCommenterProfile.load(imageBytes) {
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.ic_user_placeholder)
                        error(R.drawable.ic_user_placeholder)
                    }
                } catch (e: Exception) {
                    binding.ivCommenterProfile.setImageResource(R.drawable.ic_user_placeholder)
                }
            } else {
                binding.ivCommenterProfile.setImageResource(R.drawable.ic_user_placeholder)
            }
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean = oldItem.commentId == newItem.commentId
        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean = oldItem == newItem
    }
}
