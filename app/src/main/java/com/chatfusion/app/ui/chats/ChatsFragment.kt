package com.chatfusion.app.ui.chats

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatfusion.app.ChatActivity
import com.chatfusion.app.User
import com.chatfusion.app.UserAdapter
import com.chatfusion.app.databinding.FragmentChatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import androidx.navigation.fragment.findNavController
import com.chatfusion.app.ChatRoom
import com.chatfusion.app.R

class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userAdapter: UserAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadUsers()
        setupRequestButton()
        observeRequestsCount()
    }

    private fun setupRequestButton() {
        binding.btnRequests.setOnClickListener {
            findNavController().navigate(R.id.action_chats_to_requests)
        }
    }

    private fun observeRequestsCount() {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("chatRooms")
            .whereEqualTo("status", "PENDING")
            .whereArrayContains("users", currentUserId)
            .addSnapshotListener { snapshots, _ ->
                if (!isAdded) return@addSnapshotListener
                val count = snapshots?.documents?.count { doc ->
                    val room = doc.toObject(ChatRoom::class.java)
                    room?.requestedBy != currentUserId
                } ?: 0
                
                if (count > 0) {
                    binding.tvRequestCount.text = count.toString()
                    binding.tvRequestCount.visibility = View.VISIBLE
                } else {
                    binding.tvRequestCount.visibility = View.GONE
                }
            }
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter { user ->
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("receiverId", user.uid)
            intent.putExtra("receiverName", user.name)
            startActivity(intent)
        }

        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userAdapter
        }
    }

    private fun loadUsers() {
        binding.progressBar.visibility = View.VISIBLE
        val currentUserId = auth.currentUser?.uid ?: return

        
        firestore.collection("chatRooms")
            .whereArrayContains("users", currentUserId)
            .addSnapshotListener { chatSnapshots, chatError ->
                if (!isAdded) return@addSnapshotListener
                
                if (chatError != null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error: ${chatError.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                
                val chatTimestamps = mutableMapOf<String, Long>()
                chatSnapshots?.documents?.forEach { doc ->
                    val chatRoomId = doc.id
                    val lastTimestamp = doc.getTimestamp("lastTimestamp")?.toDate()?.time ?: 0L
                    chatTimestamps[chatRoomId] = lastTimestamp
                }

                
                firestore.collection("users")
                    .addSnapshotListener { userSnapshots, userError ->
                        if (!isAdded) return@addSnapshotListener
                        binding.progressBar.visibility = View.GONE
                        
                        if (userError != null) return@addSnapshotListener

                        val users = userSnapshots?.toObjects(User::class.java) ?: emptyList()
                        val filteredUsers = users.filter { user -> 
                            val chatRoomId = if (currentUserId < user.uid) 
                                "${currentUserId}_${user.uid}" else "${user.uid}_${currentUserId}"
                            
                            // ONLY show if status is ACCEPTED
                            val status = chatSnapshots?.documents?.find { it.id == chatRoomId }?.getString("status")
                            status == "ACCEPTED" && user.uid != currentUserId
                        }

                        val sortedUsers = filteredUsers.sortedByDescending { user ->
                            val chatRoomId = if (currentUserId < user.uid) 
                                "${currentUserId}_${user.uid}" else "${user.uid}_${currentUserId}"
                            chatTimestamps[chatRoomId] ?: 0L
                        }

                        userAdapter.submitList(sortedUsers)
                    }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
