package com.homemonitor.parent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homemonitor.parent.data.repository.AuthRepository
import com.homemonitor.parent.data.repository.ParentUserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Initial : AuthUiState()
    object Loading : AuthUiState()
    data class Authenticated(val session: ParentUserSession) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    object Unauthenticated : AuthUiState()
}

class AuthViewModel(
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkCurrentSession()
    }

    fun checkCurrentSession() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val session = authRepo.getSessionDetails()
            if (session != null) {
                _uiState.value = AuthUiState.Authenticated(session)
            } else {
                _uiState.value = AuthUiState.Unauthenticated
            }
        }
    }

    fun signIn(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Email and password are required")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepo.signIn(email, pass)
            result.onSuccess { session ->
                _uiState.value = AuthUiState.Authenticated(session)
            }.onFailure { err ->
                _uiState.value = AuthUiState.Error(err.message ?: "Authentication failed")
            }
        }
    }

    fun register(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Email and password are required")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepo.register(email, pass)
            result.onSuccess { session ->
                _uiState.value = AuthUiState.Authenticated(session)
            }.onFailure { err ->
                _uiState.value = AuthUiState.Error(err.message ?: "Registration failed")
            }
        }
    }

    fun signOut() {
        authRepo.signOut()
        _uiState.value = AuthUiState.Unauthenticated
    }
}
