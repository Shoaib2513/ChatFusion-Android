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
import com.chatfusion.app.ChatRoom
import com.chatfusion.app.User
import com.chatfusion.app.UserAdapter
import com.chatfusion.app.databinding.FragmentRequestsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RequestsFragment : Fragment() {

    private var _binding: FragmentRequestsBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var requestAdapter: UserAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadRequests()

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        requestAdapter = UserAdapter(showChatDetails = false) { user ->
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("receiverId", user.uid)
            intent.putExtra("receiverName", user.name)
            startActivity(intent)
        }

        binding.rvRequests.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = requestAdapter
        }
    }

    private fun loadRequests() {
        binding.progressBar.visibility = View.VISIBLE
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection("chatRooms")
            .whereEqualTo("status", "PENDING")
            .whereArrayContains("users", currentUserId)
            .addSnapshotListener { snapshots, error ->
                if (!isAdded) return@addSnapshotListener
                binding.progressBar.visibility = View.GONE
                
                if (error != null) {
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val requesterIds = snapshots?.documents?.mapNotNull { doc ->
                    val room = doc.toObject(ChatRoom::class.java)
                    if (room?.requestedBy != currentUserId) {
                        room?.users?.find { it != currentUserId }
                    } else null
                } ?: emptyList()

                if (requesterIds.isEmpty()) {
                    binding.tvNoRequests.visibility = View.VISIBLE
                    requestAdapter.submitList(emptyList())
                    return@addSnapshotListener
                }

                binding.tvNoRequests.visibility = View.GONE
                
                firestore.collection("users")
                    .whereIn("uid", requesterIds)
                    .addSnapshotListener { userSnapshots, userError ->
                        if (!isAdded) return@addSnapshotListener
                        if (userError != null) return@addSnapshotListener

                        val users = userSnapshots?.toObjects(User::class.java) ?: emptyList()
                        requestAdapter.submitList(users)
                    }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}