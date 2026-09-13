package com.remotecamera.viewer.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthManager(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
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
