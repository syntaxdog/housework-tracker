package com.housework.tracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.housework.tracker.MainActivity
import com.housework.tracker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HouseworkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // 로그인된 사용자의 Firestore 문서에 토큰 저장
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .set(mapOf("fcmToken" to token), SetOptions.merge())
                    .await()
            } catch (_: Exception) {
                // Firestore 저장 실패 무시
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        createNotificationChannel()

        val type = message.data["type"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (type == "chat_message") {
            // 채팅: data-only 메시지에서 정보 추출
            val title = message.data["senderName"] ?: "채팅"
            val body = message.data["messagePreview"] ?: ""

            // 고정 ID로 하나의 알림을 업데이트하며 InboxStyle로 메시지 스태킹
            chatMessageHistory.add(body)
            if (chatMessageHistory.size > 5) {
                chatMessageHistory.removeAt(0)
            }

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
            for (line in chatMessageHistory) {
                inboxStyle.addLine(line)
            }
            if (chatMessageHistory.size > 1) {
                inboxStyle.setSummaryText("${chatMessageHistory.size}개의 메시지")
            }

            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(inboxStyle)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(CHAT_NOTIFICATION_ID, notificationBuilder.build())
        } else {
            // 다른 알림 (체크, 리마인더 등): notification 필드에서 추출
            val notification = message.notification ?: return
            val title = notification.title ?: "집안일 트래커"
            val body = notification.body ?: ""

            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "집안일 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "집안일 체크 및 리마인더 알림"
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "housework_notifications"
        private const val CHAT_NOTIFICATION_ID = 1001
        private val chatMessageHistory = mutableListOf<String>()

        fun clearChatNotificationHistory() {
            chatMessageHistory.clear()
        }
    }
}
