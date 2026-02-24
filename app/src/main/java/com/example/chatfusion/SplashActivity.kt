package com.example.chatfusion

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.chatfusion.databinding.ActivitySplashBinding
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fade in animation for logo and title
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeIn.duration = 1500
        binding.splashLogo.startAnimation(fadeIn)
        binding.splashTitle.startAnimation(fadeIn)

        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, 2500)
    }

    private fun checkUserStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // User is signed in, go to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // No user is signed in, go to LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}