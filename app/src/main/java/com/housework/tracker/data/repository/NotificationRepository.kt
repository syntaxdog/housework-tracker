package com.housework.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationPreferences(
    val partnerCheckEnabled: Boolean = true,
    val dailyReminderEnabled: Boolean = true,
    val chatNotificationEnabled: Boolean = true
)

private val Context.notificationDataStore by preferencesDataStore(name = "notification_settings")

@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val partnerCheckKey = booleanPreferencesKey("partner_check_enabled")
    private val dailyReminderKey = booleanPreferencesKey("daily_reminder_enabled")
    private val chatNotificationKey = booleanPreferencesKey("chat_notification_enabled")

    val preferences: Flow<NotificationPreferences> = context.notificationDataStore.data.map { prefs ->
        NotificationPreferences(
            partnerCheckEnabled = prefs[partnerCheckKey] ?: true,
            dailyReminderEnabled = prefs[dailyReminderKey] ?: true,
            chatNotificationEnabled = prefs[chatNotificationKey] ?: true
        )
    }

    suspend fun setPartnerCheckEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[partnerCheckKey] = enabled
        }
        syncToFirestore("partnerCheckEnabled", enabled)
    }

    suspend fun setDailyReminderEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[dailyReminderKey] = enabled
        }
        syncToFirestore("dailyReminderEnabled", enabled)
    }

    suspend fun setChatNotificationEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[chatNotificationKey] = enabled
        }
        syncToFirestore("chatNotificationEnabled", enabled)
    }

    private suspend fun syncToFirestore(field: String, enabled: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .update("notificationSettings.$field", enabled)
                .await()
        } catch (_: Exception) {
            // 문서가 없거나 필드가 없으면 set으로 생성
            try {
                firestore.collection("users").document(userId)
                    .set(
                        mapOf("notificationSettings" to mapOf(field to enabled)),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            } catch (_: Exception) {
                // Firestore 동기화 실패는 무시 (로컬 설정은 이미 저장됨)
            }
        }
    }
}
