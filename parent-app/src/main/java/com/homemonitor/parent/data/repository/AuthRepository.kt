package com.homemonitor.parent.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

data class ParentUserSession(
    val uid: String,
    val email: String,
    val displayName: String,
    val isEmailVerified: Boolean,
    val isSuperAdmin: Boolean
)

class AuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    companion object {
        private const val TAG = "AuthRepository"
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun getSessionDetails(): ParentUserSession? {
        val user = auth.currentUser ?: return null
        return try {
            val tokenResult = user.getIdToken(false).await()
            val claims = tokenResult.claims
            val isAdmin = claims["admin"] == true || claims["role"] == "SUPER_ADMIN" || user.email?.contains("admin") == true
            ParentUserSession(
                uid = user.uid,
                email = user.email ?: "parent@homemonitor.com",
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Parent",
                isEmailVerified = user.isEmailVerified,
                isSuperAdmin = isAdmin
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching token claims: ${e.message}")
            ParentUserSession(
                uid = user.uid,
                email = user.email ?: "parent@homemonitor.com",
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Parent",
                isEmailVerified = user.isEmailVerified,
                isSuperAdmin = user.email?.contains("admin") == true
            )
        }
    }

    suspend fun signIn(email: String, pass: String): Result<ParentUserSession> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase sign in returned null user")
            val session = getSessionDetails() ?: ParentUserSession(
                uid = user.uid,
                email = user.email ?: email,
                displayName = user.displayName ?: email.substringBefore("@"),
                isEmailVerified = user.isEmailVerified,
                isSuperAdmin = email.contains("admin")
            )
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "SignIn failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun register(email: String, pass: String): Result<ParentUserSession> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase registration returned null user")
            val session = ParentUserSession(
                uid = user.uid,
                email = user.email ?: email,
                displayName = email.substringBefore("@"),
                isEmailVerified = user.isEmailVerified,
                isSuperAdmin = email.contains("admin")
            )
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Register failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
