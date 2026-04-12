package com.chatfusion.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chatfusion.app.databinding.ItemMessageAiBinding
import com.chatfusion.app.databinding.ItemMessageUserBinding

class MessageAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ChatMessage.User -> VIEW_TYPE_USER
            is ChatMessage.AI -> VIEW_TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_USER) {
            val binding = ItemMessageUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            UserViewHolder(binding)
        } else {
            val binding = ItemMessageAiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AIViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is UserViewHolder && message is ChatMessage.User) {
            holder.bind(message)
        } else if (holder is AIViewHolder && message is ChatMessage.AI) {
            holder.bind(message)
        }
    }

    class UserViewHolder(private val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage.User) {
            binding.tvMessageUser.text = message.text
        }
    }

    class AIViewHolder(private val binding: ItemMessageAiBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage.AI) {
            if (message.isLoading) {
                binding.tvMessageAi.visibility = View.GONE
                binding.progressAi.visibility = View.VISIBLE
            } else {
                binding.tvMessageAi.visibility = View.VISIBLE
                binding.progressAi.visibility = View.GONE
                binding.tvMessageAi.text = message.text
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean = oldItem == newItem
    }
}
