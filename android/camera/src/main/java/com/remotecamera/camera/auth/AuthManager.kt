package com.remotecamera.camera.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthManager(private val customAuth: FirebaseAuth? = null) {

    private val auth: FirebaseAuth
        get() = customAuth ?: FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = try { auth.currentUser } catch (e: Exception) { null }

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        try {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                trySend(firebaseAuth.currentUser)
            }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        } catch (e: Exception) {
            close(e)
        }
    }

    suspend fun signInAnonymouslyIfNeeded(): Result<FirebaseUser> {
        return try {
            val user = auth.currentUser
            if (user != null) {
                Result.success(user)
            } else {
                val authResult = auth.signInAnonymously().await()
                val newUser = authResult.user ?: throw IllegalStateException("Firebase auth returned null user")
                Result.success(newUser)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
