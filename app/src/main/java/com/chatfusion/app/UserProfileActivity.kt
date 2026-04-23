package com.chatfusion.app

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.chatfusion.app.databinding.ActivityUserProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

import androidx.recyclerview.widget.LinearLayoutManager
import com.chatfusion.app.ui.home.PostAdapter

class UserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var postAdapter: PostAdapter
    private var receiverId: String? = null
    private var receiverName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        receiverId = intent.getStringExtra("receiverId")
        receiverName = intent.getStringExtra("receiverName")

        if (receiverId == auth.currentUser?.uid) {
            binding.actionsContainer.visibility = View.GONE
        }

        setupUI()
        loadUserProfile()
        checkFollowStatus()
        checkExistingRequest()
        loadUserPosts()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnAction.setOnClickListener {
            it.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_click))
            sendRequest()
        }
        binding.btnFollow.setOnClickListener {
            it.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_click))
            toggleFollow()
        }

        postAdapter = PostAdapter({ post ->
            // Handle post click if needed, e.g., open comments
        }, { userId, userName ->
            if (userId != receiverId) {
                val intent = android.content.Intent(this, UserProfileActivity::class.java).apply {
                    putExtra("receiverId", userId)
                    putExtra("receiverName", userName)
                }
                startActivity(intent)
            }
        })
        binding.rvUserPosts.apply {
            layoutManager = LinearLayoutManager(this@UserProfileActivity)
            adapter = postAdapter
        }
    }

    private fun loadUserPosts() {
        val uid = receiverId ?: return
        
        firestore.collection("posts")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                
                val posts = snapshot?.toObjects(Post::class.java) ?: emptyList()
                postAdapter.submitList(posts)
                
                if (posts.isEmpty()) {
                    binding.tvPostsLabel.text = "No posts yet"
                } else {
                    binding.tvPostsLabel.text = "Posts (${posts.size})"
                }
            }
    }

    private fun checkFollowStatus() {
        val currentUserId = auth.currentUser?.uid ?: return
        val targetId = receiverId ?: return

        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.toObject(User::class.java)
                val isFollowing = user?.following?.contains(targetId) == true
                updateFollowButton(isFollowing)
            }
    }

    private fun updateFollowButton(isFollowing: Boolean) {
        if (isFollowing) {
            binding.btnFollow.text = "Unfollow"
            binding.btnFollow.setIconResource(0)
            binding.btnFollow.setStrokeColorResource(R.color.premium_error)
            binding.btnFollow.setTextColor(getColor(R.color.premium_error))
        } else {
            binding.btnFollow.text = "Follow"
            binding.btnFollow.setIconResource(0)
            binding.btnFollow.setStrokeColorResource(R.color.colorPrimary)
            binding.btnFollow.setTextColor(getColor(R.color.colorPrimary))
        }
    }

    private fun toggleFollow() {
        val currentUserId = auth.currentUser?.uid ?: return
        val targetId = receiverId ?: return

        binding.progressBar.visibility = View.VISIBLE
        val userRef = firestore.collection("users").document(currentUserId)
        val targetRef = firestore.collection("users").document(targetId)

        firestore.runTransaction { transaction ->
            val user = transaction.get(userRef).toObject(User::class.java)
            val target = transaction.get(targetRef).toObject(User::class.java)

            if (user != null && target != null) {
                val userFollowing = user.following.toMutableList()
                val targetFollowers = target.followers.toMutableList()
                
                val isFollowing = userFollowing.contains(targetId)
                
                if (isFollowing) {
                    userFollowing.remove(targetId)
                    targetFollowers.remove(currentUserId)
                } else {
                    userFollowing.add(targetId)
                    targetFollowers.add(currentUserId)
                }
                
                transaction.update(userRef, "following", userFollowing)
                transaction.update(targetRef, "followers", targetFollowers)
                !isFollowing
            } else {
                throw Exception("User data not found")
            }
        }.addOnSuccessListener { isNowFollowing ->
            binding.progressBar.visibility = View.GONE
            updateFollowButton(isNowFollowing)
            loadUserProfile() // Refresh counts
        }.addOnFailureListener { e ->
            binding.progressBar.visibility = View.GONE
            val errorMessage = when {
                e.message?.contains("permission-denied") == true -> "Permission denied: Update your Firestore rules."
                else -> "Action failed: ${e.localizedMessage}"
            }
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadUserProfile() {
        val uid = receiverId ?: return
        binding.progressBar.visibility = View.VISIBLE
        
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE
                val user = snapshot.toObject(User::class.java) ?: return@addOnSuccessListener
                
                binding.tvProfileName.text = user.name
                binding.tvProfileBio.text = user.bio.ifEmpty { "No bio available" }
                binding.tvFollowersCount.text = user.followers.size.toString()
                binding.tvFollowingCount.text = user.following.size.toString()
                
                if (user.online) {
                    binding.tvProfileStatus.text = "Online"
                    binding.tvProfileStatus.setTextColor(getColor(R.color.online_indicator))
                } else {
                    binding.tvProfileStatus.text = "Offline"
                    binding.tvProfileStatus.setTextColor(getColor(R.color.text_secondary))
                }

                loadProfileImage(user.profileImageUrl)
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadProfileImage(imageData: String) {
        if (imageData.isEmpty()) {
            binding.ivProfileImg.setImageResource(R.drawable.ic_profile)
            return
        }
        try {
            val cleanBase64 = if (imageData.contains(",")) imageData.substringAfter(",") else imageData
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            binding.ivProfileImg.load(imageBytes) {
                crossfade(true)
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
                transformations(CircleCropTransformation())
            }
        } catch (e: Exception) {
            binding.ivProfileImg.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun checkExistingRequest() {
        val currentUserId = auth.currentUser?.uid ?: return
        val otherId = receiverId ?: return
        val chatRoomId = if (currentUserId < otherId) "${currentUserId}_${otherId}" else "${otherId}_${currentUserId}"

        firestore.collection("chatRooms").document(chatRoomId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val room = snapshot.toObject(ChatRoom::class.java)
                    if (room?.status == "PENDING") {
                        if (room.requestedBy == currentUserId) {
                            binding.btnAction.isEnabled = false
                            binding.btnAction.text = "Request Pending"
                            binding.tvRequestStatus.visibility = View.VISIBLE
                            binding.tvRequestStatus.text = "A message request has already been sent."
                        } else {
                            binding.btnAction.text = "Accept Request"
                            binding.btnAction.setOnClickListener {
                                acceptRequest(chatRoomId)
                            }
                        }
                    } else if (room?.status == "ACCEPTED") {
                        binding.btnAction.text = "Go to Chat"
                        binding.btnAction.setOnClickListener {
                            val intent = android.content.Intent(this, ChatActivity::class.java)
                            intent.putExtra("receiverId", receiverId)
                            intent.putExtra("receiverName", receiverName)
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        binding.btnAction.text = "Send Request"
                        binding.btnAction.setOnClickListener { sendRequest() }
                    }
                } else {
                    binding.btnAction.text = "Send Request"
                    binding.btnAction.setOnClickListener { sendRequest() }
                }
            }
    }

    private fun acceptRequest(chatRoomId: String) {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("chatRooms").document(chatRoomId)
            .update("status", "ACCEPTED")
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Request Accepted", Toast.LENGTH_SHORT).show()
                checkExistingRequest()
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to accept request", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendRequest() {
        val currentUserId = auth.currentUser?.uid ?: return
        val otherId = receiverId ?: return
        val chatRoomId = if (currentUserId < otherId) "${currentUserId}_${otherId}" else "${otherId}_${currentUserId}"

        binding.progressBar.visibility = View.VISIBLE
        binding.btnAction.isEnabled = false

        val chatRoom = ChatRoom(
            chatRoomId = chatRoomId,
            users = listOf(currentUserId, otherId),
            status = "PENDING",
            requestedBy = currentUserId,
            lastMessage = "Sent a message request",
            lastTimestamp = com.google.firebase.Timestamp.now()
        )

        firestore.collection("chatRooms").document(chatRoomId)
            .set(chatRoom, SetOptions.merge())
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Request Sent Successfully", Toast.LENGTH_SHORT).show()
                binding.btnAction.text = "Request Sent"
                binding.tvRequestStatus.visibility = View.VISIBLE
                binding.tvRequestStatus.text = "Wait for $receiverName to accept your request."
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                binding.btnAction.isEnabled = true
                Toast.makeText(this, "Failed to send request", Toast.LENGTH_SHORT).show()
            }
    }
}
