package com.example.watermetertest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.example.watermetertest.presentation.viewmodels.LoginViewModel
import com.example.watermetertest.databinding.ActivityLoginBinding

import dagger.hilt.android.AndroidEntryPoint

/**
 * Login Activity - Professional implementation for job interview demonstration
 * 
 * Features:
 * - Material Design 3 UI with proper spacing and visual hierarchy
 * - Real-time input validation with helpful error messages
 * - Password visibility toggle
 * - IME actions for better keyboard navigation
 * - Accessibility support with content descriptions
 * - Loading states and error handling
 * - MVVM architecture with ViewModel and LiveData
 * - Hilt dependency injection
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_ALL_PERMISSIONS = 100
    }

    private lateinit var binding: ActivityLoginBinding
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if already logged in
        if (loginViewModel.isLoggedIn()) {
            navigateToMain()
            return
        }

        setupInputFields()
        setupClickListeners()
        observeViewModel()
        checkAndRequestPermissions()
    }

    /**
     * Setup input fields with real-time validation and IME actions
     */
    private fun setupInputFields() {
        // Email field - real-time validation
        binding.emailEditText.addTextChangedListener { text ->
            loginViewModel.validateEmail(text?.toString())
        }
        
        // Email IME action - move to password field
        binding.emailEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.passwordEditText.requestFocus()
                true
            } else {
                false
            }
        }

        // Password field - real-time validation
        binding.passwordEditText.addTextChangedListener { text ->
            loginViewModel.validatePassword(text?.toString())
        }
        
        // Password IME action - trigger login
        binding.passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                performLogin()
                true
            } else {
                false
            }
        }
    }

    /**
     * Setup click listeners for interactive elements
     */
    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener { 
            performLogin()
        }
    }

    /**
     * Observe ViewModel LiveData for reactive UI updates
     */
    private fun observeViewModel() {
        // Loading state
        loginViewModel.getLoading().observe(this) { isLoading ->
            setLoadingState(isLoading ?: false)
        }

        // Login result
        loginViewModel.getLoginResult().observe(this) { result ->
            loginViewModel.setLoadingFinished()
            when (result) {
                "success" -> navigateToMain()
                null -> {} // No result yet
                else -> {
                    // Show error message
                    Toast.makeText(
                        this, 
                        getString(R.string.error_login_failed), 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Login state
        loginViewModel.getLoginState().observe(this) { isLoggedIn ->
            if (isLoggedIn == true) {
                navigateToMain()
            }
        }

        // Email validation errors
        loginViewModel.emailError.observe(this) { error ->
            binding.emailInput.error = error
        }

        // Password validation errors
        loginViewModel.passwordError.observe(this) { error ->
            binding.passwordInput.error = error
        }
        
        // Form validity - enable/disable login button
        loginViewModel.isFormValid.observe(this) { isValid ->
            // Button is always clickable, but validation happens on click
            // This provides better UX feedback
        }
    }

    /**
     * Perform login with current input values
     */
    private fun performLogin() {
        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()

        // Hide keyboard
        binding.emailEditText.clearFocus()
        binding.passwordEditText.clearFocus()

        // Validation is handled in ViewModel
        loginViewModel.login(email, password)
    }

    /**
     * Update UI based on loading state
     */
    private fun setLoadingState(isLoading: Boolean) {
        binding.loginButton.isEnabled = !isLoading
        binding.loginButton.text = if (isLoading) "" else getString(R.string.login_button)
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        
        // Disable input fields during loading
        binding.emailEditText.isEnabled = !isLoading
        binding.passwordEditText.isEnabled = !isLoading
    }

    /**
     * Navigate to main activity after successful login
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Check and request necessary permissions for the app
     */
    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            arrayOf(
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            // Pre-Android 12 permissions
            arrayOf(
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val hasAllPermissions = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasAllPermissions) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_ALL_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (!allPermissionsGranted) {
                Toast.makeText(
                    this,
                    "All permissions are required for the app to work properly",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}