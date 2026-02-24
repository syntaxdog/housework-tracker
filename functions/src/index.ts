import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

/**
 * 상대방 체크 알림
 * completions 문서가 생성되면 상대방에게 FCM 푸시 알림 발송
 */
export const onItemCompleted = onDocumentCreated(
  "houses/{houseId}/dailyLogs/{dateId}/completions/{completionId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const completion = snapshot.data();
    const houseId = event.params.houseId;

    // 가정 멤버 조회
    const houseDoc = await db.collection("houses").doc(houseId).get();
    if (!houseDoc.exists) return;

    const members: string[] = houseDoc.data()?.members || [];
    const partnerId = members.find((id) => id !== completion.userId);
    if (!partnerId) return;

    // 상대방 정보 조회
    const partnerDoc = await db.collection("users").doc(partnerId).get();
    if (!partnerDoc.exists) return;

    const partnerData = partnerDoc.data();
    const fcmToken: string | undefined = partnerData?.fcmToken;
    if (!fcmToken) return;

    // 상대방이 알림을 꺼놨으면 발송하지 않음
    if (partnerData?.notificationSettings?.partnerCheckEnabled === false) return;

    // 푸시 알림 발송
    try {
      await messaging.send({
        token: fcmToken,
        notification: {
          title: `${completion.userName}님이 완료했어요!`,
          body: `${completion.itemName} (${completion.points}점)`,
        },
        data: {
          type: "partner_check",
          houseId: houseId,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "housework_notifications",
          },
        },
      });
    } catch (error: unknown) {
      // 토큰이 만료된 경우 삭제
      const errorCode = (error as { code?: string }).code;
      if (
        errorCode === "messaging/registration-token-not-registered" ||
        errorCode === "messaging/invalid-registration-token"
      ) {
        await db.collection("users").doc(partnerId).update({ fcmToken: "" });
      }
      console.error("FCM 전송 실패:", error);
    }
  }
);

/**
 * 매일 저녁 9시 (KST) 미완료 리마인더
 * 오늘 기록이 없는 멤버에게 알림 발송
 */
export const dailyReminder = onSchedule(
  {
    schedule: "0 21 * * *",
    timeZone: "Asia/Seoul",
  },
  async () => {
    // 오늘 날짜 (KST)
    const now = new Date();
    const kstOffset = 9 * 60 * 60 * 1000;
    const kstDate = new Date(now.getTime() + kstOffset);
    const dateStr = kstDate.toISOString().split("T")[0]; // YYYY-MM-DD

    // 모든 가정 조회
    const housesSnapshot = await db.collection("houses").get();

    const sendPromises: Promise<void>[] = [];

    for (const houseDoc of housesSnapshot.docs) {
      const members: string[] = houseDoc.data().members || [];
      if (members.length === 0) continue;

      // 오늘 기록 조회
      const dailyLogDoc = await db
        .collection("houses")
        .doc(houseDoc.id)
        .collection("dailyLogs")
        .doc(dateStr)
        .get();

      const scores: Record<string, number> =
        dailyLogDoc.data()?.scores || {};

      // 기록이 없는 멤버에게 알림
      for (const memberId of members) {
        if (scores[memberId] && scores[memberId] > 0) continue;

        const sendNotification = async () => {
          const userDoc = await db.collection("users").doc(memberId).get();
          const userData = userDoc.data();
          const fcmToken: string | undefined = userData?.fcmToken;
          if (!fcmToken) return;

          // 사용자가 리마인더를 꺼놨으면 발송하지 않음
          if (userData?.notificationSettings?.dailyReminderEnabled === false) return;

          try {
            await messaging.send({
              token: fcmToken,
              notification: {
                title: "집안일 리마인더",
                body: "오늘 아직 집안일을 안 했어요! 체크해보세요 ✨",
              },
              data: {
                type: "daily_reminder",
                houseId: houseDoc.id,
              },
              android: {
                priority: "high",
                notification: {
                  channelId: "housework_notifications",
                },
              },
            });
          } catch (error) {
            console.error(`리마인더 전송 실패 (${memberId}):`, error);
          }
        };

        sendPromises.push(sendNotification());
      }
    }

    await Promise.all(sendPromises);
    console.log(`리마인더 전송 완료: ${sendPromises.length}건`);
  }
);

/**
 * 채팅 메시지 알림
 * chatMessages 문서가 생성되면 상대방에게 FCM 푸시 알림 발송
 */
export const onChatMessage = onDocumentCreated(
  "houses/{houseId}/chatMessages/{messageId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const message = snapshot.data();
    const houseId = event.params.houseId;

    // 가정 멤버 조회
    const houseDoc = await db.collection("houses").doc(houseId).get();
    if (!houseDoc.exists) return;

    const members: string[] = houseDoc.data()?.members || [];
    const partnerId = members.find((id) => id !== message.senderId);
    if (!partnerId) return;

    // 상대방 정보 조회
    const partnerDoc = await db.collection("users").doc(partnerId).get();
    if (!partnerDoc.exists) return;

    const partnerData = partnerDoc.data();
    const fcmToken: string | undefined = partnerData?.fcmToken;
    if (!fcmToken) return;

    // 상대방이 채팅 알림을 꺼놨으면 발송하지 않음
    if (partnerData?.notificationSettings?.chatNotificationEnabled === false) return;

    // 상대방이 채팅 화면을 보고 있으면 알림 발송하지 않음
    if (partnerData?.chatActive === true) return;

    // 메시지 미리보기 (이모티콘 처리 + 30자 제한)
    const isEmoji = /^\[EMOJI:.+\]$/.test(message.text);
    const preview = isEmoji
      ? "이모티콘"
      : message.text.length > 30
        ? message.text.substring(0, 30) + "..."
        : message.text;

    // 푸시 알림 발송 (data-only: 클라이언트에서 알림 스태킹 제어)
    try {
      await messaging.send({
        token: fcmToken,
        data: {
          type: "chat_message",
          houseId: houseId,
          senderName: message.senderName,
          messagePreview: preview,
        },
        android: {
          priority: "high",
        },
      });
    } catch (error: unknown) {
      const errorCode = (error as { code?: string }).code;
      if (
        errorCode === "messaging/registration-token-not-registered" ||
        errorCode === "messaging/invalid-registration-token"
      ) {
        await db.collection("users").doc(partnerId).update({ fcmToken: "" });
      }
      console.error("채팅 알림 전송 실패:", error);
    }
  }
);
