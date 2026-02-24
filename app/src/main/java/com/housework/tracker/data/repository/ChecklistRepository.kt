package com.housework.tracker.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.housework.tracker.data.model.ChecklistItem
import com.housework.tracker.data.model.DailyCompletion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChecklistRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val kstZone = ZoneId.of("Asia/Seoul")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun getChecklistItems(houseId: String): Flow<List<ChecklistItem>> = callbackFlow {
        val ref = firestore.collection("houses").document(houseId)
            .collection("checklistItems")
            .orderBy("order")
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val items = snapshot?.documents?.map { doc ->
                doc.toObject(ChecklistItem::class.java)?.copy(id = doc.id) ?: ChecklistItem()
            } ?: emptyList()
            trySend(items)
        }
        awaitClose { listener.remove() }
    }

    fun getCompletions(houseId: String, date: LocalDate): Flow<List<DailyCompletion>> = callbackFlow {
        val dateStr = date.format(dateFormatter)
        val ref = firestore.collection("houses").document(houseId)
            .collection("dailyLogs").document(dateStr)
            .collection("completions")
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val completions = snapshot?.documents?.map { doc ->
                doc.toObject(DailyCompletion::class.java)?.copy(id = doc.id) ?: DailyCompletion()
            } ?: emptyList()
            trySend(completions)
        }
        awaitClose { listener.remove() }
    }

    suspend fun checkItem(
        houseId: String,
        date: LocalDate,
        item: ChecklistItem,
        userId: String,
        userName: String
    ) {
        val dateStr = date.format(dateFormatter)
        val monthStr = YearMonth.from(date).format(monthFormatter)

        firestore.runTransaction { transaction ->
            val dailyLogRef = firestore.collection("houses").document(houseId)
                .collection("dailyLogs").document(dateStr)
            val completionRef = dailyLogRef.collection("completions").document()
            val monthlyRef = firestore.collection("houses").document(houseId)
                .collection("monthlySummary").document(monthStr)

            // 현재 점수 읽기
            val dailyLogSnap = transaction.get(dailyLogRef)
            val monthlySnap = transaction.get(monthlyRef)

            @Suppress("UNCHECKED_CAST")
            val dailyScores = (dailyLogSnap.get("scores") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
            @Suppress("UNCHECKED_CAST")
            val monthlyScoresMap = (monthlySnap.get("scores") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()

            // 완료 기록 추가
            val completion = DailyCompletion(
                id = completionRef.id,
                itemId = item.id,
                itemName = item.name,
                points = item.points,
                userId = userId,
                userName = userName,
                completedAt = Timestamp.now()
            )
            transaction.set(completionRef, completion)

            // 일간 요약 업데이트 (read-then-write)
            dailyScores[userId] = (dailyScores[userId] ?: 0L) + item.points.toLong()
            transaction.set(dailyLogRef, mapOf("scores" to dailyScores), SetOptions.merge())

            // 월간 요약 업데이트 (read-then-write)
            monthlyScoresMap[userId] = (monthlyScoresMap[userId] ?: 0L) + item.points.toLong()
            transaction.set(monthlyRef, mapOf("scores" to monthlyScoresMap), SetOptions.merge())
        }.await()
    }

    suspend fun uncheckItem(
        houseId: String,
        date: LocalDate,
        completion: DailyCompletion
    ) {
        val dateStr = date.format(dateFormatter)
        val monthStr = YearMonth.from(date).format(monthFormatter)

        firestore.runTransaction { transaction ->
            val dailyLogRef = firestore.collection("houses").document(houseId)
                .collection("dailyLogs").document(dateStr)
            val completionRef = dailyLogRef.collection("completions").document(completion.id)
            val monthlyRef = firestore.collection("houses").document(houseId)
                .collection("monthlySummary").document(monthStr)

            // 현재 점수 읽기
            val dailyLogSnap = transaction.get(dailyLogRef)
            val monthlySnap = transaction.get(monthlyRef)

            @Suppress("UNCHECKED_CAST")
            val dailyScores = (dailyLogSnap.get("scores") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
            @Suppress("UNCHECKED_CAST")
            val monthlyScoresMap = (monthlySnap.get("scores") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()

            // 완료 기록 삭제
            transaction.delete(completionRef)

            // 일간 요약 감소 (read-then-write)
            dailyScores[completion.userId] = maxOf(0L, (dailyScores[completion.userId] ?: 0L) - completion.points.toLong())
            transaction.set(dailyLogRef, mapOf("scores" to dailyScores), SetOptions.merge())

            // 월간 요약 감소 (read-then-write)
            monthlyScoresMap[completion.userId] = maxOf(0L, (monthlyScoresMap[completion.userId] ?: 0L) - completion.points.toLong())
            transaction.set(monthlyRef, mapOf("scores" to monthlyScoresMap), SetOptions.merge())
        }.await()
    }

    suspend fun addChecklistItem(houseId: String, name: String, points: Int, category: String) {
        val itemsRef = firestore.collection("houses").document(houseId)
            .collection("checklistItems")
        val snapshot = itemsRef.get().await()
        val nextOrder = (snapshot.documents.mapNotNull { it.getLong("order")?.toInt() }.maxOrNull() ?: 0) + 1

        itemsRef.document().set(
            ChecklistItem(
                name = name,
                points = points,
                category = category,
                isDefault = false,
                order = nextOrder
            )
        ).await()
    }

    suspend fun updateItemOrders(houseId: String, items: List<ChecklistItem>) {
        val batch = firestore.batch()
        val itemsRef = firestore.collection("houses").document(houseId)
            .collection("checklistItems")
        items.forEachIndexed { index, item ->
            if (item.id.isNotEmpty()) {
                batch.update(itemsRef.document(item.id), "order", index)
            }
        }
        batch.commit().await()
    }

    suspend fun getMonthDailyScores(houseId: String, yearMonth: YearMonth): Map<LocalDate, Map<String, Long>> {
        val firstDay = yearMonth.atDay(1).format(dateFormatter)
        val lastDay = yearMonth.atEndOfMonth().format(dateFormatter)
        val snapshot = firestore.collection("houses").document(houseId)
            .collection("dailyLogs")
            .whereGreaterThanOrEqualTo(com.google.firebase.firestore.FieldPath.documentId(), firstDay)
            .whereLessThanOrEqualTo(com.google.firebase.firestore.FieldPath.documentId(), lastDay)
            .get().await()

        val result = mutableMapOf<LocalDate, Map<String, Long>>()
        for (doc in snapshot.documents) {
            @Suppress("UNCHECKED_CAST")
            val scores = doc.get("scores") as? Map<String, Long>
            if (scores != null && scores.isNotEmpty()) {
                val date = LocalDate.parse(doc.id, dateFormatter)
                result[date] = scores
            }
        }
        return result
    }

    suspend fun getDailyScores(houseId: String, date: LocalDate): Map<String, Long> {
        val dateStr = date.format(dateFormatter)
        val doc = firestore.collection("houses").document(houseId)
            .collection("dailyLogs").document(dateStr).get().await()
        @Suppress("UNCHECKED_CAST")
        return (doc.get("scores") as? Map<String, Long>) ?: emptyMap()
    }

    suspend fun getMonthlyScores(houseId: String, yearMonth: YearMonth): Map<String, Long> {
        val monthStr = yearMonth.format(monthFormatter)
        val doc = firestore.collection("houses").document(houseId)
            .collection("monthlySummary").document(monthStr).get().await()
        @Suppress("UNCHECKED_CAST")
        return (doc.get("scores") as? Map<String, Long>) ?: emptyMap()
    }
}
