package com.example.watermetertest.presentation.viewmodels

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.example.watermetertest.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class LoginViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var application: Application

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = LoginViewModel(application)
    }

    @Test
    fun `login with empty username sets username error`() {
        // Given
        val username = ""
        val password = "password123"
        val observer = mock(Observer::class.java) as Observer<String>
        viewModel.usernameError.observeForever(observer)

        // When
        viewModel.login(username, password)

        // Then
        verify(observer).onChanged(Constants.ERROR_USERNAME_REQUIRED)
    }

    @Test
    fun `login with empty password sets password error`() {
        // Given
        val username = "user123"
        val password = ""
        val observer = mock(Observer::class.java) as Observer<String>
        viewModel.passwordError.observeForever(observer)

        // When
        viewModel.login(username, password)

        // Then
        verify(observer).onChanged(Constants.ERROR_PASSWORD_REQUIRED)
    }

    @Test
    fun `onUsernameChanged clears username error`() {
        // Given
        val observer = mock(Observer::class.java) as Observer<String?>
        viewModel.usernameError.observeForever(observer)
        
        // When
        viewModel.onUsernameChanged()

        // Then
        verify(observer).onChanged(null)
    }
}
