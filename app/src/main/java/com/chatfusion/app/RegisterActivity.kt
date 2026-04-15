package com.chatfusion.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.chatfusion.app.BuildConfig
import com.chatfusion.app.R
import com.chatfusion.app.databinding.ActivityRegisterBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var credentialManager: CredentialManager

    companion object {
        private const val TAG = "RegisterActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        credentialManager = CredentialManager.create(this)

        
        if (auth.currentUser != null) {
            navigateToMain()
            return
        }

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        binding.btnRegister.setOnClickListener { 
            val animation = AnimationUtils.loadAnimation(this, R.anim.button_click)
            binding.btnRegister.startAnimation(animation)
            registerUser()
        }

        binding.btnGoogleRegister.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this, R.anim.button_click)
            binding.btnGoogleRegister.startAnimation(animation)
            signUpWithGoogle()
        }

        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { 
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun registerUser() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (validateInput(name, email, password, confirmPassword)) {
            showLoading(true)

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        val user = User(
                            uid = firebaseUser?.uid ?: "",
                            name = name,
                            nameLower = name.lowercase(),
                            email = email,
                            profileImageUrl = "", 
                            online = true
                        )
                        
                        saveUserToFirestore(user)
                    } else {
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        showLoading(false)
                    }
                }
        }
    }

    private fun signUpWithGoogle() {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

        if (serverClientId.isEmpty()) {
            Toast.makeText(this, "Configuration Error: Please set GOOGLE_WEB_CLIENT_ID in local.properties", Toast.LENGTH_LONG).show()
            return
        }

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                showLoading(true)
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@RegisterActivity
                )
                handleGoogleSignInResult(result.credential)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google Sign-Up failed: ${e.message}", e)
                val errorMessage = when (e) {
                    is NoCredentialException -> "No Google accounts found. Please ensure you are signed in on this device."
                    is GetCredentialCancellationException -> "Sign-in cancelled."
                    else -> "Google Sign-Up failed. Please check your internet or configuration."
                }
                Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
        }
    }

    private fun handleGoogleSignInResult(credential: androidx.credentials.Credential) {
        if (credential is GoogleIdTokenCredential) {
            val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
            auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        if (task.result?.additionalUserInfo?.isNewUser == true) {
                            val user = User(
                                uid = firebaseUser?.uid ?: "",
                                name = firebaseUser?.displayName ?: "Google User",
                                nameLower = (firebaseUser?.displayName ?: "Google User").lowercase(),
                                email = firebaseUser?.email ?: "",
                                profileImageUrl = firebaseUser?.photoUrl?.toString() ?: "",
                                online = true
                            )
                            saveUserToFirestore(user)
                        } else {
                            navigateToMain()
                        }
                    } else {
                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                        showLoading(false)
                    }
                }
        }
    }

    private fun saveUserToFirestore(user: User) {
        firestore.collection("users").document(user.uid).set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                navigateToMain()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_LONG).show()
                showLoading(false)
            }
    }

    private fun validateInput(name: String, email: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (name.isEmpty()) {
            binding.nameLayout.error = "Full name is required"
            isValid = false
        } else {
            binding.nameLayout.error = null
        }

        if (email.isEmpty()) {
            binding.emailLayout.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "Invalid email format"
            isValid = false
        } else {
            binding.emailLayout.error = null
        }

        if (password.isEmpty()) {
            binding.passwordLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            binding.passwordLayout.error = null
        }

        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordLayout.error = "Please confirm your password"
            isValid = false
        } else if (confirmPassword != password) {
            binding.confirmPasswordLayout.error = "Passwords do not match"
            isValid = false
        } else {
            binding.confirmPasswordLayout.error = null
        }

        return isValid
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
        binding.btnGoogleRegister.isEnabled = !isLoading
    }
}
