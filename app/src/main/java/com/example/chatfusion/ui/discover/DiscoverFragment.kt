package com.chatfusion.app.ui.discover

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatfusion.app.ChatActivity
import com.chatfusion.app.User
import com.chatfusion.app.UserAdapter
import com.chatfusion.app.databinding.FragmentDiscoverBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var suggestionAdapter: UserAdapter
    private lateinit var searchAdapter: UserAdapter
    
    private var suggestionListener: ListenerRegistration? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

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
        binding.suggestionProgressBar.visibility = View.VISIBLE
        
        suggestionListener = firestore.collection("users")
            .limit(20)
            .addSnapshotListener { snapshot, e ->
                if (!isAdded) return@addSnapshotListener
                binding.suggestionProgressBar.visibility = View.GONE
                
                if (e != null) {
                    Log.e("DiscoverFragment", "Error loading suggestions", e)
                    Toast.makeText(context, "Connection error", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                
                val users = snapshot?.toObjects(User::class.java) ?: emptyList()
                val filteredList = users.filter { it.uid != currentUserId }
                
                Log.d("DiscoverFragment", "Loaded ${filteredList.size} suggestions")
                suggestionAdapter.submitList(filteredList)
            }
    }

    private fun setupSearch() {
        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                
                // Debounce search to save Firestore reads
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { searchUsers(query) }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun searchUsers(query: String) {
        if (!isAdded) return
        
        if (query.isEmpty()) {
            searchAdapter.submitList(emptyList())
            binding.tvNoResults.visibility = View.GONE
            binding.searchProgressBar.visibility = View.GONE
            return
        }

        binding.searchProgressBar.visibility = View.VISIBLE
        binding.tvNoResults.visibility = View.GONE

        val lowerQuery = query.lowercase()

        firestore.collection("users")
            .whereGreaterThanOrEqualTo("nameLower", lowerQuery)
            .whereLessThanOrEqualTo("nameLower", lowerQuery + "\uf8ff")
            .limit(15)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                binding.searchProgressBar.visibility = View.GONE
                
                val users = snapshot.toObjects(User::class.java)
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                val filteredResults = users.filter { it.uid != currentUserId }
                
                searchAdapter.submitList(filteredResults)
                binding.tvNoResults.visibility = if (filteredResults.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e("DiscoverFragment", "Error searching users", e)
                binding.searchProgressBar.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        suggestionListener?.remove()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        _binding = null
    }
}
