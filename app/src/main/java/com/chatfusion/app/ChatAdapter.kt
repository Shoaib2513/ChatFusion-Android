package com.chatfusion.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Build
import coil.load
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chatfusion.app.databinding.ItemMessageLeftBinding
import com.chatfusion.app.databinding.ItemMessageRightBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale
import com.chatfusion.app.R

class ChatAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val binding = ItemMessageRightBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SentViewHolder(binding)
        } else {
            val binding = ItemMessageLeftBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ReceivedViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is SentViewHolder) holder.bind(message)
        else if (holder is ReceivedViewHolder) holder.bind(message)
    }

    class SentViewHolder(private val binding: ItemMessageRightBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            if (message.messageType == "GIF") {
                binding.ivMedia.visibility = View.VISIBLE
                binding.tvMessage.visibility = if (message.message.isEmpty()) View.GONE else View.VISIBLE
                binding.tvMessage.text = message.message
                
                val imageLoader = ImageLoader.Builder(binding.root.context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()
                
                binding.ivMedia.load(message.mediaUrl, imageLoader)
            } else {
                binding.ivMedia.visibility = View.GONE
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvMessage.text = message.message
            }

            binding.tvTime.text = message.timestamp?.toDate()?.let { 
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
            } ?: ""

            if (message.seen) {
                
                binding.ivSeenStatus.setImageResource(R.drawable.ic_check_double)
                binding.ivSeenStatus.setColorFilter(Color.parseColor("#00E676")) 
            } else {
                
                binding.ivSeenStatus.setImageResource(R.drawable.ic_check)
                binding.ivSeenStatus.setColorFilter(Color.WHITE)
            }
        }
    }

    class ReceivedViewHolder(private val binding: ItemMessageLeftBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            if (message.messageType == "GIF") {
                binding.ivMedia.visibility = View.VISIBLE
                binding.tvMessage.visibility = if (message.message.isEmpty()) View.GONE else View.VISIBLE
                binding.tvMessage.text = message.message

                val imageLoader = ImageLoader.Builder(binding.root.context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()

                binding.ivMedia.load(message.mediaUrl, imageLoader)
            } else {
                binding.ivMedia.visibility = View.GONE
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvMessage.text = message.message
            }

            binding.tvTime.text = message.timestamp?.toDate()?.let { 
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
            } ?: ""
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = 
            oldItem.timestamp == newItem.timestamp && oldItem.senderId == newItem.senderId
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }
}
