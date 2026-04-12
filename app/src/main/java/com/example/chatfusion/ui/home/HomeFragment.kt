package com.chatfusion.app.ui.home

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatfusion.app.CommentsActivity
import com.chatfusion.app.CreatePostActivity
import com.chatfusion.app.Post
import com.chatfusion.app.databinding.FragmentHomeBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlin.math.sqrt

class HomeFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var postAdapter: PostAdapter
    private var snapshotListener: ListenerRegistration? = null

    // Motion Sensors - Shake to Refresh
    private lateinit var sensorManager: SensorManager
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var lastShakeTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
        
        // Initialize Sensor Manager
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

        setupRecyclerView()
        loadPosts()

        // Swipe Refresh Implementation
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadPosts()
        }
        
        binding.fabCreatePost.setOnClickListener {
            startActivity(Intent(requireContext(), CreatePostActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter { post ->
            val intent = Intent(requireContext(), CommentsActivity::class.java)
            intent.putExtra("POST_ID", post.postId)
            startActivity(intent)
        }
        binding.rvPosts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
        }
    }

    private fun loadPosts() {
        binding.swipeRefreshLayout.isRefreshing = true
        
        // Remove old listener to avoid multiple redundant listeners
        snapshotListener?.remove()

        snapshotListener = firestore.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                // Ensure we stop the loading animation even if error occurs
                binding.swipeRefreshLayout.isRefreshing = false
                
                if (e != null || !isAdded) {
                    return@addSnapshotListener
                }
                val posts = snapshot?.toObjects(Post::class.java) ?: emptyList()
                postAdapter.submitList(posts)
            }
    }

    // Sensor Implementation - Shake to Refresh with Debounce
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta: Float = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta

            if (acceleration > 12) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShakeTime > 3000) { // 3-second debounce to prevent spamming
                    lastShakeTime = currentTime
                    Toast.makeText(context, "Refreshing feed...", Toast.LENGTH_SHORT).show()
                    loadPosts()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        snapshotListener?.remove()
        _binding = null
    }
}
