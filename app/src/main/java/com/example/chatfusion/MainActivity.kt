package com.example.chatfusion

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatfusion.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    
    private val userAdapter = UserAdapter { user ->
        openChat(user)
    }
    
    private val searchAdapter = UserAdapter { user ->
        binding.searchView.hide()
        openChat(user)
    }

    private var allUsers = listOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupUI()
        loadUsers()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = userAdapter
        }

        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchAdapter
        }

        binding.searchView.editText.setOnEditorActionListener { v, actionId, event ->
            filterUsers(binding.searchView.text.toString())
            false
        }
        
        // Listen for text changes in search view for real-time filtering
        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun filterUsers(query: String) {
        val filteredList = allUsers.filter { 
            it.name.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true)
        }
        searchAdapter.submitList(filteredList)
    }

    private fun openChat(user: User) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("receiverId", user.uid)
        intent.putExtra("receiverName", user.name)
        startActivity(intent)
    }

    private fun loadUsers() {
        binding.progressBar.visibility = View.VISIBLE
        val currentUserId = auth.currentUser?.uid

        firestore.collection("users")
            .addSnapshotListener { snapshot, e ->
                binding.progressBar.visibility = View.GONE
                if (e != null) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val users = snapshot?.toObjects(User::class.java) ?: emptyList()
                allUsers = users.filter { it.uid != currentUserId }
                userAdapter.submitList(allUsers)
                
                if (allUsers.isEmpty()) {
                    // This happens if Firestore 'users' collection is empty or only has current user
                    Toast.makeText(this, "No other users found. Invite friends!", Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
