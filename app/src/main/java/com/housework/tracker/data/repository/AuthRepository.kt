package com.housework.tracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.housework.tracker.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        result.user?.let {
            createUserIfNotExists(it)
            saveFcmToken(it.uid)
        }
        return result.user
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser? {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        result.user?.let { saveFcmToken(it.uid) }
        return result.user
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): FirebaseUser? {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user?.let {
            createUserIfNotExists(it, displayName)
            saveFcmToken(it.uid)
        }
        return result.user
    }

    private suspend fun createUserIfNotExists(firebaseUser: FirebaseUser, displayName: String? = null) {
        val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
        if (!userDoc.exists()) {
            val user = User(
                id = firebaseUser.uid,
                displayName = displayName ?: firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                createdAt = com.google.firebase.Timestamp.now()
            )
            firestore.collection("users").document(firebaseUser.uid).set(user).await()
        }
    }

    private suspend fun saveFcmToken(userId: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            firestore.collection("users").document(userId)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .await()
        } catch (_: Exception) {
            // FCM 토큰 저장 실패 무시
        }
    }

    suspend fun getUserProfile(userId: String): User? {
        val doc = firestore.collection("users").document(userId).get().await()
        return doc.toObject(User::class.java)?.copy(id = doc.id)
    }

    fun signOut() {
        auth.signOut()
    }
}

