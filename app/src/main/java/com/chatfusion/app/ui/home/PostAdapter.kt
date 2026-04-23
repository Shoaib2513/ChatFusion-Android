package com.chatfusion.app.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class PostAdapter(
    private val onCommentClick: (Post) -> Unit,
    private val onProfileClick: (String, String) -> Unit
) : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding, onCommentClick, onProfileClick)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PostViewHolder(
        private val binding: ItemPostBinding,
        private val onCommentClick: (Post) -> Unit,
        private val onProfileClick: (String, String) -> Unit
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

            loadBase64Image(post.userProfileImage, binding.ivAuthorProfile, R.drawable.ic_user_placeholder)

            binding.ivAuthorProfile.setOnClickListener {
                onProfileClick(post.userId, post.userName)
            }
            binding.tvAuthorName.setOnClickListener {
                onProfileClick(post.userId, post.userName)
            }
            
            if (post.imageUrl.isNotEmpty()) {
                binding.ivPostImage.visibility = View.VISIBLE
                loadBase64Image(post.imageUrl, binding.ivPostImage, R.drawable.ic_placeholder)
            } else {
                binding.ivPostImage.visibility = View.GONE
            }

            val isLiked = post.likes.contains(currentUserId)
            updateLikeUI(isLiked)

            binding.layoutLike.setOnClickListener {
                val anim = AnimationUtils.loadAnimation(binding.root.context, androidx.appcompat.R.anim.abc_popup_enter)
                binding.ivLike.startAnimation(anim)
                toggleLike(post, currentUserId, isLiked)
            }

            binding.layoutComment.setOnClickListener {
                onCommentClick(post)
            }

            binding.layoutShare.setOnClickListener {
                sharePost(post)
            }

            binding.btnMoreOptions.setOnClickListener { view ->
                showMoreOptions(view, post)
            }

            if (post.aiInsight.isNotEmpty()) {
                binding.cvAiInsight.visibility = View.VISIBLE
                binding.tvAiInsight.text = post.aiInsight
            } else {
                binding.cvAiInsight.visibility = View.GONE
            }
        }

        private fun updateLikeUI(isLiked: Boolean) {
            if (isLiked) {
                binding.ivLike.setImageResource(R.drawable.ic_favorite_filled)
                binding.ivLike.setColorFilter(binding.root.context.getColor(R.color.colorPrimary))
            } else {
                binding.ivLike.setImageResource(R.drawable.ic_favorite)
                binding.ivLike.setColorFilter(binding.root.context.getColor(R.color.text_secondary))
            }
        }

        private fun loadBase64Image(base64String: String, imageView: android.widget.ImageView, placeholder: Int) {
            if (base64String.isEmpty()) {
                imageView.setImageResource(placeholder)
                return
            }

            try {
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

        private fun sharePost(post: Post) {
            val context = binding.root.context
            val shareText = "Check out this post on ChatFusion by ${post.userName}:\n\n${post.content}\n\nAI Insight: ${post.aiInsight}"
            
            if (post.imageUrl.isNotEmpty()) {
                shareImageAndText(context, post, shareText)
            } else {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "Share via"))
            }
        }

        private fun shareImageAndText(context: Context, post: Post, shareText: String) {
            val drawable = binding.ivPostImage.drawable
            if (drawable is BitmapDrawable) {
                val bitmap = drawable.bitmap
                try {
                    val cachePath = File(context.cacheDir, "images")
                    cachePath.mkdirs()
                    val file = File(cachePath, "shared_image.png")
                    val stream = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.close()

                    val imageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "Share via"))
            }
        }

        private fun showMoreOptions(view: View, post: Post) {
            val context = view.context
            val popup = PopupMenu(context, view)
            
            if (post.userId == auth.currentUser?.uid) {
                popup.menu.add("Delete Post")
            }
            popup.menu.add("Report Post")
            popup.menu.add("Copy Text")

            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Delete Post" -> {
                        firestore.collection("posts").document(post.postId).delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                            }
                    }
                    "Report Post" -> {
                        Toast.makeText(context, "Post reported", Toast.LENGTH_SHORT).show()
                    }
                    "Copy Text" -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Post Content", post.content)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            popup.show()
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem.postId == newItem.postId
        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem == newItem
    }
}
