package com.housework.tracker.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.housework.tracker.data.model.ChatMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun getMessages(houseId: String): Flow<List<ChatMessage>> = callbackFlow {
        val ref = firestore.collection("houses").document(houseId)
            .collection("chatMessages")
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .limit(100)
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val messages = snapshot?.documents?.map { doc ->
                doc.toObject(ChatMessage::class.java)?.copy(id = doc.id) ?: ChatMessage()
            } ?: emptyList()
            trySend(messages)
        }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(houseId: String, senderId: String, senderName: String, text: String) {
        val message = ChatMessage(
            senderId = senderId,
            senderName = senderName,
            text = text,
            sentAt = Timestamp.now()
        )
        firestore.collection("houses").document(houseId)
            .collection("chatMessages")
            .document()
            .set(message)
            .await()
    }
}
