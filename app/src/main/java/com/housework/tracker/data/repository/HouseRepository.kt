package com.housework.tracker.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.housework.tracker.data.model.House
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class HouseRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun createHouse(name: String, userId: String): House {
        val inviteCode = generateInviteCode()
        val houseRef = firestore.collection("houses").document()
        val house = House(
            id = houseRef.id,
            name = name,
            inviteCode = inviteCode,
            members = listOf(userId),
            createdAt = Timestamp.now()
        )

        // 가정 생성 + 사용자 연결을 트랜잭션으로 원자적 처리
        firestore.runTransaction { transaction ->
            transaction.set(houseRef, house)
            transaction.set(
                firestore.collection("users").document(userId),
                mapOf("houseId" to houseRef.id),
                SetOptions.merge()
            )
        }.await()

        // 기본 체크리스트 항목은 트랜잭션 후 batch로 생성
        createDefaultChecklistItems(houseRef.id)

        return house
    }

    suspend fun joinHouse(inviteCode: String, userId: String): House? {
        // 먼저 초대 코드로 가정 조회 (query는 트랜잭션 외부에서)
        val snapshot = firestore.collection("houses")
            .whereEqualTo("inviteCode", inviteCode)
            .get().await()

        val houseDoc = snapshot.documents.firstOrNull() ?: return null
        val houseRef = firestore.collection("houses").document(houseDoc.id)

        return firestore.runTransaction { transaction ->
            // 트랜잭션 내에서 최신 데이터 다시 읽기
            val freshDoc = transaction.get(houseRef)
            val house = freshDoc.toObject(House::class.java)?.copy(id = freshDoc.id) ?: return@runTransaction null

            if (house.members.size >= 2) return@runTransaction null // 최대 2명
            if (house.members.contains(userId)) return@runTransaction house // 이미 멤버

            val updatedMembers = house.members + userId
            transaction.update(houseRef, "members", updatedMembers)
            transaction.set(
                firestore.collection("users").document(userId),
                mapOf("houseId" to house.id),
                SetOptions.merge()
            )

            house.copy(members = updatedMembers)
        }.await()
    }

    suspend fun getHouse(houseId: String): House? {
        val doc = firestore.collection("houses").document(houseId).get().await()
        return doc.toObject(House::class.java)?.copy(id = doc.id)
    }

    private suspend fun createDefaultChecklistItems(houseId: String) {
        val defaults = listOf(
            mapOf("name" to "설거지", "points" to 3, "category" to "주방", "isDefault" to true, "order" to 1),
            mapOf("name" to "청소기", "points" to 3, "category" to "청소", "isDefault" to true, "order" to 2),
            mapOf("name" to "검은빨래", "points" to 2, "category" to "빨래", "isDefault" to true, "order" to 3),
            mapOf("name" to "흰빨래", "points" to 2, "category" to "빨래", "isDefault" to true, "order" to 4),
            mapOf("name" to "수건빨래", "points" to 2, "category" to "빨래", "isDefault" to true, "order" to 5),
            mapOf("name" to "건조기", "points" to 1, "category" to "빨래", "isDefault" to true, "order" to 6),
        )
        val batch = firestore.batch()
        defaults.forEach { item ->
            val ref = firestore.collection("houses").document(houseId)
                .collection("checklistItems").document()
            batch.set(ref, item)
        }
        batch.commit().await()
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 혼동 문자 제외 (0/O, 1/I)
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
