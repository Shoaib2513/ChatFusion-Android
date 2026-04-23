package com.chatfusion.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.chatfusion.app.CommentsActivity
import com.chatfusion.app.ConnectivityActivity
import com.chatfusion.app.EditProfileActivity
import com.chatfusion.app.LoginActivity
import com.chatfusion.app.Post
import com.chatfusion.app.R
import com.chatfusion.app.User
import com.chatfusion.app.WebViewActivity
import com.chatfusion.app.databinding.FragmentProfileBinding
import com.chatfusion.app.ui.home.PostAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var postAdapter: PostAdapter
    
    private var userListener: ListenerRegistration? = null
    private var postsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupUI()
        loadUserProfile()
        loadMyPosts()
    }

    private fun setupUI() {
        postAdapter = PostAdapter({ post ->
            val intent = Intent(requireContext(), CommentsActivity::class.java)
            intent.putExtra("POST_ID", post.postId)
            startActivity(intent)
        }, { _, _ ->
            // Already on profile
        })
        binding.rvMyPosts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
            isNestedScrollingEnabled = false
        }

        binding.btnLogout.setOnClickListener {
            updateStatusAndSignOut()
        }

        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        binding.btnMoodPicker.setOnClickListener {
            showMoodPickerDialog()
        }

        
        binding.btnConnectivity.setOnClickListener {
            startActivity(Intent(requireContext(), ConnectivityActivity::class.java))
        }

        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun showMoodPickerDialog() {
        val moods = arrayOf("Neutral", "Happy", "Busy", "Focused", "Relaxed")
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Select Your Mood")
        builder.setItems(moods) { _, which ->
            val selectedMood = moods[which]
            updateUserMood(selectedMood)
        }
        builder.show()
    }

    private fun updateUserMood(mood: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .update("mood", mood)
            .addOnSuccessListener {
                if (isAdded) {
                    android.widget.Toast.makeText(requireContext(), "Mood updated to $mood", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateStatusAndSignOut() {
        val uid = auth.currentUser?.uid ?: return
        val updates = mapOf(
            "online" to false,
            "lastSeen" to com.google.firebase.Timestamp.now()
        )
        
        firestore.collection("users").document(uid).update(updates)
            .addOnCompleteListener {
                auth.signOut()
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            }
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action is permanent and will delete all your data.")
            .setPositiveButton("Delete") { _, _ -> deleteUserAccount() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUserAccount() {
        val user = auth.currentUser ?: return
        val userId = user.uid

        firestore.collection("users").document(userId).delete()
            .addOnSuccessListener {
                user.delete().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT)
                            .show()
                        startActivity(Intent(requireContext(), LoginActivity::class.java))
                        requireActivity().finish()
                    } else {
                        Toast.makeText(
                            context,
                            "Authentication failed. Please login again to delete account.",
                            Toast.LENGTH_LONG
                        ).show()
                        auth.signOut()
                        startActivity(Intent(requireContext(), LoginActivity::class.java))
                        requireActivity().finish()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to delete user data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserProfile() {
        val currentUserId = auth.currentUser?.uid ?: return
        userListener = firestore.collection("users").document(currentUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || !isAdded) return@addSnapshotListener
                
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java) ?: return@addSnapshotListener
                    
                    binding.tvProfileName.text = user.name
                    binding.tvProfileEmail.text = user.email
                    binding.tvFollowersCount.text = user.followers.size.toString()
                    binding.tvFollowingCount.text = user.following.size.toString()
                    
                    binding.tvProfileBio.text = user.bio
                    binding.tvProfileBio.visibility = if (user.bio.isNullOrEmpty()) View.GONE else View.VISIBLE

                    loadProfileImage(user.profileImageUrl)
                }
            }
    }

    private fun loadProfileImage(imageData: String) {
        if (imageData.isEmpty()) {
            binding.ivProfileLarge.setImageResource(R.drawable.ic_profile)
            return
        }
        try {
            val cleanBase64 = if (imageData.contains(",")) imageData.substringAfter(",") else imageData
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            binding.ivProfileLarge.load(imageBytes) {
                crossfade(true)
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
                transformations(CircleCropTransformation())
            }
        } catch (ignored: Exception) {
            binding.ivProfileLarge.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun loadMyPosts() {
        val currentUserId = auth.currentUser?.uid ?: return
        postsListener = firestore.collection("posts")
            .whereEqualTo("userId", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || !isAdded) return@addSnapshotListener
                
                val posts = snapshot?.toObjects(Post::class.java) ?: emptyList()
                binding.tvPostsCount.text = posts.size.toString()
                postAdapter.submitList(posts)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        postsListener?.remove()
        _binding = null
    }
}
