package com.chatfusion.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chatfusion.app.databinding.ItemMessageLeftBinding
import com.chatfusion.app.databinding.ItemMessageRightBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale
import com.chatfusion.app.R
import android.content.Intent
import android.provider.CalendarContract
import java.util.regex.Pattern

class ChatAdapter(
    private val onMessageLongClick: (Message, Int) -> Unit = { _, _ -> }
) : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback()) {

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
        if (holder is SentViewHolder) holder.bind(message, position, onMessageLongClick)
        else if (holder is ReceivedViewHolder) holder.bind(message, position, onMessageLongClick)
        
        setAnimation(holder.itemView, position)
    }

    private var lastPosition = -1
    private fun setAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            val animation = android.view.animation.AnimationUtils.loadAnimation(viewToAnimate.context, android.R.anim.fade_in)
            animation.duration = 400
            viewToAnimate.startAnimation(animation)
            lastPosition = position
        }
    }

    class SentViewHolder(private val binding: ItemMessageRightBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int, onLongClick: (Message, Int) -> Unit) {
            binding.ivMedia.visibility = View.GONE
            binding.tvMessage.visibility = View.VISIBLE
            
            binding.root.setOnLongClickListener {
                onLongClick(message, position)
                true
            }
            
            if (message.isBurn) {
                binding.tvMessage.text = "🔥 Burn Message (Tap to view)"
                binding.tvMessage.alpha = 0.5f
                binding.root.setOnClickListener {
                    binding.tvMessage.text = message.message
                    binding.tvMessage.alpha = 1.0f
                }
            } else {
                binding.tvMessage.text = message.message
                binding.tvMessage.alpha = 1.0f
                binding.root.setOnClickListener(null)
            }

            val moodColor = when (message.senderMood) {
                "Happy" -> "#FFF9C4"
                "Busy" -> "#FFCDD2"
                "Focused" -> "#E3F2FD"
                "Relaxed" -> "#C8E6C9"
                else -> null
            }
            
            moodColor?.let { 
                binding.cardMessage.setCardBackgroundColor(Color.parseColor(it))
                binding.tvMessage.setTextColor(Color.BLACK)
            } ?: run {
                binding.cardMessage.setCardBackgroundColor(Color.parseColor("#6200EE"))
                binding.tvMessage.setTextColor(Color.WHITE)
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

            checkForCalendarEvents(message.message, binding.btnAddCalendar)
        }

        private fun deleteBurnMessage(message: Message) {
            val senderId = message.senderId
            val receiverId = message.receiverId
            val chatRoomId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"
            
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("chatRooms").document(chatRoomId)
                .collection("messages")
                .whereEqualTo("timestamp", message.timestamp)
                .get()
                .addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                }
        }

        private fun checkForCalendarEvents(text: String, button: View) {
            val dateRegex = "\\b(\\d{1,2}/\\d{1,2}/\\d{4}|\\d{4}-\\d{2}-\\d{2})\\b"
            val timeRegex = "\\b(\\d{1,2}:\\d{2}\\s*(?i)(am|pm)?)\\b"
            val pattern = Pattern.compile("$dateRegex|$timeRegex")
            val matcher = pattern.matcher(text)

            if (matcher.find()) {
                button.visibility = View.VISIBLE
                button.setOnClickListener {
                    val intent = Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, "Chat Event")
                        .putExtra(CalendarContract.Events.DESCRIPTION, "From Chat Fusion: $text")
                    it.context.startActivity(intent)
                }
            } else {
                button.visibility = View.GONE
            }
        }
    }

    class ReceivedViewHolder(private val binding: ItemMessageLeftBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int, onLongClick: (Message, Int) -> Unit) {
            binding.ivMedia.visibility = View.GONE
            binding.tvMessage.visibility = View.VISIBLE

            binding.root.setOnLongClickListener {
                onLongClick(message, position)
                true
            }
            
            if (message.isBurn) {
                binding.tvMessage.text = "🔥 Burn Message (Tap to view)"
                binding.tvMessage.alpha = 0.5f
                binding.root.setOnClickListener {
                    binding.tvMessage.text = message.message
                    binding.tvMessage.alpha = 1.0f
                    // Notify server to delete message after viewing
                    deleteBurnMessage(message)
                }
            } else {
                binding.tvMessage.text = message.message
                binding.tvMessage.alpha = 1.0f
                binding.root.setOnClickListener(null)
            }

            val moodColor = when (message.senderMood) {
                "Happy" -> "#FFF9C4"
                "Busy" -> "#FFCDD2"
                "Focused" -> "#E3F2FD"
                "Relaxed" -> "#C8E6C9"
                else -> null
            }

            moodColor?.let {
                binding.cardMessage.setCardBackgroundColor(Color.parseColor(it))
                binding.tvMessage.setTextColor(Color.BLACK)
            } ?: run {
                binding.cardMessage.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
                binding.tvMessage.setTextColor(Color.BLACK)
            }

            binding.tvTime.text = message.timestamp?.toDate()?.let { 
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
            } ?: ""

            checkForCalendarEvents(message.message, binding.btnAddCalendar)
        }

        private fun deleteBurnMessage(message: Message) {
            val senderId = message.senderId
            val receiverId = message.receiverId
            val chatRoomId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"
            
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("chatRooms").document(chatRoomId)
                .collection("messages")
                .whereEqualTo("timestamp", message.timestamp)
                .get()
                .addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                }
        }

        private fun checkForCalendarEvents(text: String, button: View) {
            val dateRegex = "\\b(\\d{1,2}/\\d{1,2}/\\d{4}|\\d{4}-\\d{2}-\\d{2})\\b"
            val timeRegex = "\\b(\\d{1,2}:\\d{2}\\s*(?i)(am|pm)?)\\b"
            val pattern = Pattern.compile("$dateRegex|$timeRegex")
            val matcher = pattern.matcher(text)

            if (matcher.find()) {
                button.visibility = View.VISIBLE
                button.setOnClickListener {
                    val intent = Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, "Chat Event")
                        .putExtra(CalendarContract.Events.DESCRIPTION, "From Chat Fusion: $text")
                    it.context.startActivity(intent)
                }
            } else {
                button.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = 
            oldItem.timestamp == newItem.timestamp && oldItem.senderId == newItem.senderId
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }
}
