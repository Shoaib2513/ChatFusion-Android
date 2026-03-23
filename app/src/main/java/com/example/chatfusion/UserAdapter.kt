package com.example.chatfusion

import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.chatfusion.databinding.ItemUserBinding

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
            binding.tvEmail.text = user.email
            
            loadProfileImage(user.profileImageUrl)
            
            binding.root.setOnClickListener { onUserClick(user) }
        }

        private fun loadProfileImage(imageData: String) {
            if (imageData.isEmpty()) {
                binding.ivProfile.setImageResource(R.drawable.ic_profile)
                return
            }

            try {
                if (imageData.startsWith("data:image")) {
                    binding.ivProfile.load(imageData) {
                        crossfade(true)
                        placeholder(R.drawable.ic_profile)
                        error(R.drawable.ic_profile)
                    }
                } else {
                    val imageBytes = Base64.decode(imageData, Base64.DEFAULT)
                    binding.ivProfile.load(imageBytes) {
                        crossfade(true)
                        placeholder(R.drawable.ic_profile)
                        error(R.drawable.ic_profile)
                    }
                }
            } catch (e: Exception) {
                binding.ivProfile.setImageResource(R.drawable.ic_profile)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = oldItem == newItem
    }
}
