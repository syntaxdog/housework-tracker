# 🏠 Housework Tracker

> 집안일 체크리스트 & 점수 관리 안드로이드 앱

커플이 함께 사용하는 집안일 관리 앱입니다.  
매일 완료한 집안일을 체크하고, 점수를 기록하며, 실시간으로 동기화됩니다.

---

## ✨ 주요 기능

### 📋 일일 체크리스트
- 매일 초기화되는 집안일 체크리스트
- 항목 체크 시 수행자 기록 및 점수 부여
- 본인이 체크한 항목만 언체크 가능 (상대방 항목은 읽기 전용)
- 커스텀 항목 추가/수정/삭제 (추천 항목 제공)

### 🏆 점수 시스템
- 항목별 점수 설정 (1~10점)
- 일간/월간 점수 집계 및 랭킹
- Firestore 트랜잭션을 활용한 원자적 점수 업데이트

### 📅 달력 & 통계
- 월간 달력 뷰에서 일별 활동량 확인
- 색상 밀도로 활동량 시각화 (GitHub 잔디 스타일)
- 날짜별 상세 체크리스트 내역 조회

### 💬 실시간 채팅
- 커플 간 실시간 채팅 기능
- 커스텀 이모티콘 지원

### 🔔 푸시 알림 (FCM)
- 상대방 체크 완료 시 알림
- 매일 저녁 9시 미완료 리마인더
- 채팅 메시지 알림
- 알림 종류별 개별 ON/OFF

### 🔗 가정 연결
- 초대 코드로 커플 매칭 (8자리, 24시간 만료, 5회 시도 제한)
- 초대 코드 검증은 서버에서 처리

### 🌙 다크모드
- Material 3 다이나믹 테마
- 시스템 설정 연동 / 수동 토글

---

## 🛠 기술 스택

| 영역 | 기술 |
|------|------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM (ViewModel + StateFlow) |
| **DI** | Hilt |
| **Backend** | Firebase (Firestore, Auth, FCM) |
| **Server** | Cloud Functions (TypeScript) |
| **Auth** | Firebase Authentication (Google / Email) |
| **Realtime Sync** | Firestore Snapshot Listener |
| **Local Storage** | DataStore Preferences |

---

## 🏗 아키텍처

```
app/src/main/java/com/housework/tracker/
├── di/                     # Hilt DI 모듈
├── data/
│   ├── model/              # 데이터 클래스
│   └── repository/         # Firestore Repository 계층
├── service/                # FCM 메시징 서비스
├── ui/
│   ├── theme/              # Material 3 테마 (Color, Type, Theme)
│   ├── navigation/         # Navigation Compose
│   ├── auth/               # 로그인
│   ├── house/              # 가정 설정 & 초대
│   ├── checklist/          # 체크리스트 (메인)
│   ├── calendar/           # 달력 & 통계
│   ├── chat/               # 실시간 채팅
│   ├── settings/           # 설정
│   └── main/               # 메인 화면
└── MainActivity.kt

functions/src/              # Firebase Cloud Functions
└── index.ts                # FCM 알림 트리거 (체크 알림, 리마인더, 채팅)
```

---

## 📊 Firestore 데이터 모델

```
houses/{houseId}
├── checklistItems/{itemId}                          # 체크리스트 항목 정의
├── dailyLogs/{YYYY-MM-DD}                           # 일간 점수 요약
│   └── completions/{id}                             # 개별 완료 기록 (스냅샷)
├── monthlySummary/{YYYY-MM}                         # 월간 점수 요약
└── chatMessages/{messageId}                         # 채팅 메시지

users/{userId}                                       # 사용자 프로필 & FCM 토큰
```

- **스냅샷 방식**: 완료 기록에 항목명/점수를 함께 저장하여, 항목 수정/삭제 후에도 과거 기록 보존
- **Firestore Security Rules**: 같은 가정 멤버만 데이터 접근 가능, 필드 검증 적용

---

## 🚀 시작하기

### 사전 요구사항
- Android Studio Ladybug 이상
- JDK 17
- Firebase 프로젝트

### 설정

1. **레포지토리 클론**
   ```bash
   git clone https://github.com/<username>/housework-tracker.git
   ```

2. **Firebase 설정**
   - [Firebase Console](https://console.firebase.google.com/)에서 프로젝트 생성
   - Android 앱 등록 (패키지명: `com.housework.tracker`)
   - `google-services.json`을 `app/` 디렉토리에 추가

3. **Cloud Functions 배포**
   ```bash
   cd functions
   npm install
   firebase deploy --only functions
   ```

4. **Firestore Security Rules 배포**
   ```bash
   firebase deploy --only firestore:rules
   ```

5. **빌드 & 실행**
   - Android Studio에서 프로젝트를 열고 Run

---

## 📱 앱 환경

| 항목 | 값 |
|------|-----|
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| Kotlin | 2.0+ |
| Compose BOM | Latest |

---

## 📄 License

This project is for personal/portfolio use.
