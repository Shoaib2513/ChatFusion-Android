package com.example.chatfusion.ui.discover

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatfusion.ChatActivity
import com.example.chatfusion.User
import com.example.chatfusion.UserAdapter
import com.example.chatfusion.databinding.FragmentDiscoverBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var suggestionAdapter: UserAdapter
    private lateinit var searchAdapter: UserAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firestore = FirebaseFirestore.getInstance()

        setupRecyclerViews()
        loadSuggestions()
        setupSearch()
    }

    private fun setupRecyclerViews() {
        // Use showChatDetails = false for discovery to show bio instead of chat history
        suggestionAdapter = UserAdapter(showChatDetails = false) { user -> openChat(user) }
        binding.rvSuggestions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = suggestionAdapter
            setHasFixedSize(true)
        }

        searchAdapter = UserAdapter(showChatDetails = false) { user ->
            binding.searchView.hide()
            openChat(user)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
        }
    }

    private fun openChat(user: User) {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("receiverId", user.uid)
        intent.putExtra("receiverName", user.name)
        startActivity(intent)
    }

    private fun loadSuggestions() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        firestore.collection("users")
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                val users = snapshot.toObjects(User::class.java)
                suggestionAdapter.submitList(users.filter { it.uid != currentUserId })
            }
    }

    private fun setupSearch() {
        binding.searchView.editText.setOnEditorActionListener { v, _, _ ->
            searchUsers(v.text.toString())
            false
        }

        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchUsers(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun searchUsers(query: String) {
        val searchQuery = query.trim()
        if (searchQuery.isEmpty()) {
            searchAdapter.submitList(emptyList())
            return
        }

        firestore.collection("users")
            .whereGreaterThanOrEqualTo("name", searchQuery)
            .whereLessThanOrEqualTo("name", searchQuery + "\uf8ff")
            .limit(15)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                val users = snapshot.toObjects(User::class.java)
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                searchAdapter.submitList(users.filter { it.uid != currentUserId })
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
