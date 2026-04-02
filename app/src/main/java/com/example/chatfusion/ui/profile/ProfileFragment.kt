package com.chatfusion.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.chatfusion.app.CommentsActivity
import com.chatfusion.app.EditProfileActivity
import com.chatfusion.app.LoginActivity
import com.chatfusion.app.Post
import com.chatfusion.app.R
import com.chatfusion.app.User
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
        postAdapter = PostAdapter { post ->
            val intent = Intent(requireContext(), CommentsActivity::class.java)
            intent.putExtra("POST_ID", post.postId)
            startActivity(intent)
        }
        binding.rvMyPosts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
            // Important for CoordinatorLayout scrolling behavior
            isNestedScrollingEnabled = false
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
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
                    
                    // Update Bio
                    binding.tvProfileBio.text = user.bio
                    binding.tvProfileBio.visibility = if (user.bio.isEmpty()) View.GONE else View.VISIBLE

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
