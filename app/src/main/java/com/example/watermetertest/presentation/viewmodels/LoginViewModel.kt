package com.example.watermetertest.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.watermetertest.R
import com.example.watermetertest.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val application: Application
) : ViewModel() {
    
    private val loading = MutableLiveData<Boolean>()

    private val _emailError = MutableLiveData<String?>()
    val emailError: LiveData<String?> = _emailError

    private val _passwordError = MutableLiveData<String?>()
    val passwordError: LiveData<String?> = _passwordError
    
    private val _isFormValid = MutableLiveData<Boolean>()
    val isFormValid: LiveData<Boolean> = _isFormValid

    init {
        loading.value = false
        _isFormValid.value = false
    }

    fun getLoginResult(): LiveData<String> = authRepository.getLoginResult()

    fun getLoginState(): LiveData<Boolean> = authRepository.getLoginState()

    fun getLoading(): LiveData<Boolean> = loading

    fun login(email: String?, password: String?) {
        if (!validateInput(email, password)) {
            return
        }

        loading.value = true
        // Assuming authRepository.login still takes username/password, passing email as username
        authRepository.login(email!!.trim(), password!!)
    }

    /**
     * Validates email and password inputs with comprehensive checks
     * @return true if all validations pass, false otherwise
     */
    private fun validateInput(email: String?, password: String?): Boolean {
        var isValid = true
        
        // Validate email
        when {
            email.isNullOrEmpty() -> {
                _emailError.value = application.getString(R.string.error_email_required)
                isValid = false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> {
                _emailError.value = application.getString(R.string.error_email_invalid)
                isValid = false
            }
            else -> {
                _emailError.value = null
            }
        }

        // Validate password
        when {
            password.isNullOrEmpty() -> {
                _passwordError.value = application.getString(R.string.error_password_required)
                isValid = false
            }
            password.length < 6 -> {
                _passwordError.value = application.getString(R.string.error_password_too_short)
                isValid = false
            }
            !isPasswordStrong(password) -> {
                _passwordError.value = application.getString(R.string.error_password_weak)
                isValid = false
            }
            else -> {
                _passwordError.value = null
            }
        }
        
        return isValid
    }
    
    /**
     * Checks if password meets strength requirements
     * Password should contain both letters and numbers
     */
    private fun isPasswordStrong(password: String): Boolean {
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }

    /**
     * Real-time validation for email field
     * Called when email text changes
     */
    fun validateEmail(email: String?) {
        when {
            email.isNullOrEmpty() -> {
                _emailError.value = null // Don't show error while typing
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> {
                _emailError.value = application.getString(R.string.error_email_invalid)
            }
            else -> {
                _emailError.value = null
            }
        }
        updateFormValidity(email, null)
    }

    /**
     * Real-time validation for password field
     * Called when password text changes
     */
    fun validatePassword(password: String?) {
        when {
            password.isNullOrEmpty() -> {
                _passwordError.value = null // Don't show error while typing
            }
            password.length < 6 -> {
                _passwordError.value = application.getString(R.string.error_password_too_short)
            }
            !isPasswordStrong(password) -> {
                _passwordError.value = application.getString(R.string.error_password_weak)
            }
            else -> {
                _passwordError.value = null
            }
        }
        updateFormValidity(null, password)
    }

    fun onEmailChanged() {
        _emailError.value = null
    }

    fun onPasswordChanged() {
        _passwordError.value = null
    }
    
    /**
     * Updates form validity based on current input state
     */
    private fun updateFormValidity(email: String?, password: String?) {
        val emailValid = email?.let { android.util.Patterns.EMAIL_ADDRESS.matcher(it.trim()).matches() } ?: (_emailError.value == null)
        val passwordValid = password?.let { it.length >= 6 && isPasswordStrong(it) } ?: (_passwordError.value == null)
        _isFormValid.value = emailValid && passwordValid
    }

    fun setLoadingFinished() {
        loading.value = false
    }

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()
}