package com.example.chatfusion

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.chatfusion.databinding.ItemUserBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class UserAdapter(private val onUserClick: (User) -> Unit) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
    }

    inner class UserViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.tvName.text = user.name
            binding.viewOnlineStatus.setBackgroundResource(
                if (user.online) R.drawable.bg_online_status else R.drawable.ic_launcher_background // Placeholder for offline
            )
            
            loadProfileImage(user.profileImageUrl)
            loadLastMessage(user.uid)
            
            binding.root.setOnClickListener { onUserClick(user) }
        }

        private fun loadProfileImage(imageData: String) {
            if (imageData.isEmpty()) {
                binding.ivProfile.setImageResource(R.drawable.ic_profile)
                return
            }
            try {
                val cleanBase64 = if (imageData.contains(",")) imageData.substringAfter(",") else imageData
                val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                binding.ivProfile.load(imageBytes) {
                    crossfade(true)
                    placeholder(R.drawable.ic_profile)
                    error(R.drawable.ic_profile)
                    transformations(CircleCropTransformation())
                }
            } catch (e: Exception) {
                binding.ivProfile.setImageResource(R.drawable.ic_profile)
            }
        }

        private fun loadLastMessage(receiverId: String) {
            val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val chatRoomId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"
            
            FirebaseFirestore.getInstance().collection("chatRooms").document(chatRoomId)
                .addSnapshotListener { snapshot, _ ->
                    val chatRoom = snapshot?.toObject(ChatRoom::class.java)
                    if (chatRoom != null) {
                        binding.tvLastMsg.text = chatRoom.lastMessage
                        binding.tvTime.text = chatRoom.lastTimestamp?.toDate()?.let { 
                            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
                        } ?: ""
                        
                        loadUnreadCount(chatRoomId, senderId)
                    } else {
                        binding.tvLastMsg.text = "Tap to chat"
                        binding.tvTime.text = ""
                        binding.tvUnreadCount.visibility = View.GONE
                    }
                }
        }

        private fun loadUnreadCount(chatRoomId: String, currentUserId: String) {
            FirebaseFirestore.getInstance().collection("chatRooms")
                .document(chatRoomId)
                .collection("messages")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("seen", false)
                .addSnapshotListener { snapshot, _ ->
                    val count = snapshot?.size() ?: 0
                    if (count > 0) {
                        binding.tvUnreadCount.text = count.toString()
                        binding.tvUnreadCount.visibility = View.VISIBLE
                    } else {
                        binding.tvUnreadCount.visibility = View.GONE
                    }
                }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = oldItem == newItem
    }
}
