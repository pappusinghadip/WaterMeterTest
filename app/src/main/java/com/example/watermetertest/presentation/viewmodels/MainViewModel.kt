package com.example.watermetertest.presentation.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.watermetertest.data.repository.AuthRepository
import com.example.watermetertest.data.repository.PermissionsRepository
import com.example.watermetertest.models.PermissionsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val permissionsRepository: PermissionsRepository
) : ViewModel() {

    // Auth related
    fun getLoginState(): LiveData<Boolean> = authRepository.getLoginState()

    fun logout() {
        permissionsRepository.stopPeriodicRefresh()
        authRepository.logout()
    }

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    // Permissions related
    fun getPermissions(): LiveData<PermissionsResponse> = permissionsRepository.getPermissions()

    fun getPermissionsError(): LiveData<String> = permissionsRepository.getError()

    fun getPermissionsLoading(): LiveData<Boolean> = permissionsRepository.getLoading()

    fun getSessionExpired(): LiveData<Boolean> = permissionsRepository.getSessionExpired()

    fun startPermissionsRefresh() {
        permissionsRepository.startPeriodicRefresh()
    }

    fun fetchPermissions() {
        permissionsRepository.fetchPermissions()
    }

    override fun onCleared() {
        super.onCleared()
        permissionsRepository.stopPeriodicRefresh()
    }
}