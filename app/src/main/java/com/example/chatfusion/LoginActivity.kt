package com.chatfusion.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.chatfusion.app.BuildConfig
import com.chatfusion.app.R
import com.chatfusion.app.databinding.ActivityLoginBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var credentialManager: CredentialManager

    companion object {
        private const val TAG = "LoginActivity"
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

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this, R.anim.button_click)
            binding.btnLogin.startAnimation(animation)
            loginUser()
        }

        binding.btnGoogleLogin.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this, R.anim.button_click)
            binding.btnGoogleLogin.startAnimation(animation)
            loginWithGoogle()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (validateInput(email, password)) {
            showLoading(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        navigateToMain()
                    } else {
                        val message = task.exception?.localizedMessage ?: getString(R.string.login_failed)
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        showLoading(false)
                    }
                }
        }
    }

    private fun loginWithGoogle() {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        
        if (serverClientId.isEmpty()) {
            Toast.makeText(this, "Config Error: Set GOOGLE_WEB_CLIENT_ID in local.properties", Toast.LENGTH_LONG).show()
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
                    context = this@LoginActivity
                )
                handleGoogleSignInResult(result.credential)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google Sign-In failed", e)
                val errorMessage = when (e) {
                    is NoCredentialException -> getString(R.string.no_google_accounts)
                    is GetCredentialCancellationException -> "Cancelled"
                    else -> "Login failed"
                }
                Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_SHORT).show()
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
                            firestore.collection("users").document(user.uid).set(user)
                                .addOnCompleteListener { navigateToMain() }
                        } else {
                            navigateToMain()
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.login_failed), Toast.LENGTH_SHORT).show()
                        showLoading(false)
                    }
                }
        }
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Reset Password")
        val view = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val etEmail = view.findViewById<EditText>(R.id.et_reset_email)
        builder.setView(view)
        builder.setPositiveButton("Reset") { _, _ ->
            val email = etEmail.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Email sent", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_invalid_email)
            isValid = false
        } else {
            binding.emailLayout.error = null
        }
        if (password.isEmpty()) {
            binding.passwordLayout.error = getString(R.string.error_password_required)
            isValid = false
        } else {
            binding.passwordLayout.error = null
        }
        return isValid
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.btnGoogleLogin.isEnabled = !isLoading
    }
}
