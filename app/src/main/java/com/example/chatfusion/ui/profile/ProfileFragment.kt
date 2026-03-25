package com.example.chatfusion.ui.profile

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
import com.example.chatfusion.CommentsActivity
import com.example.chatfusion.EditProfileActivity
import com.example.chatfusion.LoginActivity
import com.example.chatfusion.Post
import com.example.chatfusion.R
import com.example.chatfusion.User
import com.example.chatfusion.databinding.FragmentProfileBinding
import com.example.chatfusion.ui.home.PostAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var postAdapter: PostAdapter

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
        firestore.collection("users").document(currentUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || !isAdded) return@addSnapshotListener
                val user = snapshot?.toObject(User::class.java) ?: return@addSnapshotListener
                
                binding.tvProfileName.text = user.name
                binding.tvProfileEmail.text = user.email
                binding.tvFollowersCount.text = user.followers.size.toString()
                binding.tvFollowingCount.text = user.following.size.toString()

                loadProfileImage(user.profileImageUrl)
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
        } catch (e: Exception) {
            binding.ivProfileLarge.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun loadMyPosts() {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("posts")
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
        _binding = null
    }
}
