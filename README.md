# SyncTrip 🧳

> **여행 일정, 더 이상 한 명이 정하지 않습니다.**
> 그룹 구성원 모두의 취향을 모아 알고리즘으로 동선까지 짜주는 그룹 여행 의사결정 앱.

---

## 📌 한눈에 보기

여행을 같이 가기로 했는데, 막상 "어디 갈까?"부터 막히는 경험. SyncTrip은 그 과정을 게임처럼 가볍게 만듭니다.

1. **담기** — 각자 가고 싶은 장소를 몰래 장바구니에 담고 (서로 안 보임)
2. **투표** — 모인 장소를 스와이프로 좋아요/싫어요 (틴더처럼)
3. **자동 생성** — AI가 투표 결과로 일자별 동선을 자동으로 완성
4. **함께 다듬기** — 마음에 안 드는 장소는 대안(Plan B)으로 즉시 교체
5. **여행 중** — 가계부·정산·공유 앨범·여권 스탬프까지 한 앱에서

> 이 저장소는 **백엔드(Spring Boot)** 입니다. Android 클라이언트는 별도 저장소(`SyncTrip-kt`)에서 개발됩니다.

---

## ✨ 핵심 기능

### 🗳️ 블라인드 장바구니 → 스와이프 투표
- 다른 사람이 뭘 담았는지 **안 보이는** 상태로 장소 수집 → 눈치 보지 않는 솔직한 선택
- 모인 장소를 **LIKE / DISLIKE / SKIP** 스와이프로 투표
- WebSocket으로 누가 투표를 끝냈는지 **실시간 공유**, 전원 완료 시 자동 마감

### 🤖 AI 일정 자동 생성 (3-Step 알고리즘)
투표가 끝나면 순수 알고리즘이 일자별 동선을 자동으로 짭니다.

| 단계 | 역할 |
|---|---|
| **Step 1 — Weighted Cost** | 투표 점수 + 거리로 장소별 우선순위 계산, 채택 풀(mainPool)과 대안 풀(altPool) 분리 |
| **Step 2 — K-Means** | 지리적 군집화로 "하루에 가까운 곳끼리" 날짜별 배분 |
| **Step 3 — TSP** | Nearest Neighbor로 하루 안의 방문 순서·시간 최적화, 식사 시간대에 맛집 끼워넣기 |

- **결정론적** 설계 — 같은 투표 결과면 항상 같은 일정
- 숙소를 출발점으로 반영, 영업시간·식사 시간대·과밀 일정 등을 **경고 배지**로 안내
- 동선이 너무 튀는 장소는 **이상치로 감지**해 분리

### 🔄 Plan B — 실시간 대안 추천
- 일정 속 장소가 마음에 안 들면 길게 눌러 **주변 대안 장소를 즉시 추천**
- 반경 1km → 2km → 3km 단계적 확장, 카테고리 호환(문화↔자연) 고려
- 교체하면 해당 날짜 동선만 다시 계산 (전체 재생성 X)

### 🗺️ 장소 탐색 (Google Maps 연동)
- 국내/해외 모두 **Google Places Text Search** 기반 검색
- 검색 결과는 DB에 캐싱하고 **Redis로 6시간 캐싱**해 API 호출 최소화
- 실제 대중교통 이동시간·노선 정보는 **Google Routes API**로 계산

### 💰 가계부 & 정산
- 영수증 사진을 **Gemini Vision OCR**로 자동 인식
- 다통화 지원 (ExchangeRate-API 실시간 환율)
- **더치페이 정산** — 최소 송금 횟수로 정산 경로 최적화

### 🔔 알림 & 아카이빙
- In-App + **FCM 푸시** 알림 (멤버 합류, 투표 시작, 일정 변경, 정산 요청 등 7종)
- 해외 여행 시 현지 **공휴일 안내** (Nager.Date 연동)
- **공유 앨범** (사진 + 지도 핀), 여행 완료 시 **여권 스탬프** 자동 부여

### 🔐 인증
- **카카오 / 구글 OAuth** 로그인, JWT(Access + Refresh) 발급
- 로그아웃 시 Refresh Token을 **Redis 블랙리스트**로 서버 측 무효화

---

## 🛠️ 기술 스택

| 구분 | 사용 기술 |
|---|---|
| **언어 / 프레임워크** | Java 17, Spring Boot 3.5 |
| **데이터** | Spring Data JPA, MySQL, Redis (캐시 + 토큰 블랙리스트), H2 (테스트) |
| **인증 / 보안** | Spring Security, JWT (jjwt), OAuth 2.0 (Kakao / Google) |
| **실시간** | WebSocket (STOMP) |
| **외부 연동** | Google Places / Routes API, Gemini Vision (OCR), Firebase Admin (FCM), ExchangeRate-API, Nager.Date |
| **문서화** | SpringDoc OpenAPI (Swagger UI) |
| **인프라** | Docker Compose, GitHub Actions CI (self-hosted Windows runner) |

---

## 🏗️ 아키텍처 한눈에

```
[Android 클라이언트]
        │  REST / WebSocket
        ▼
┌─────────────────────────────────────────────┐
│            Spring Boot (com.sync)            │
│                                              │
│  Controller ──▶ Service ──▶ Repository ──▶ DB │
│                   │                          │
│                   ▼                          │
│         Algorithm (순수 함수)                │
│         Step1 ▶ Step2 ▶ Step3 ▶ Plan B        │
└─────────────────────────────────────────────┘
        │
        ├── Google Places / Routes API
        ├── Gemini Vision (영수증 OCR)
        ├── Firebase (FCM 푸시)
        └── Redis / MySQL
```

**설계 원칙**
- 알고리즘은 **순수 함수** — DB 접근 없이 입력→출력만. DB 작업은 서비스 레이어 전담
- K-Means는 **최초 1회만** 실행, 이후 편집은 TSP만 재계산 (날짜 배분 안정성 유지)
- 모든 외부 API 응답은 `places` 테이블에 캐싱해 중복 호출 방지

---

## 🚀 시작하기

### 사전 준비
- JDK 17, Docker Desktop

### 환경 변수 설정
```bash
# synctrip/.env.example 을 복사해 값 입력
cp synctrip/.env.example synctrip/.env
```
필요한 키: `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`, `JWT_SECRET`(32바이트 이상), `MYSQL_*`, `GOOGLE_MAPS_API_KEY`

### 실행
```bash
# 1. 인프라(MySQL, Redis) 실행
docker compose -f synctrip/compose.yml up -d

# 2. 애플리케이션 실행
cd synctrip
./gradlew.bat bootRun
```

### 테스트
```bash
cd synctrip
./gradlew.bat clean test     # H2 인메모리, 외부 DB 불필요
```

### API 문서
실행 후 Swagger UI에서 전체 API 확인:
```
http://localhost:8080/swagger-ui.html
```

**Spring 프로필**: `local` (개발) / `prod` (Docker 배포) / `test` (H2, create-drop)

---

## 📂 프로젝트 구조

```
synctrip/src/main/java/com/sync/
├── algorithm/        # 일정 생성 알고리즘 (순수 함수)
│   ├── step1/        #   Weighted Cost Function
│   ├── step2/        #   K-Means Clustering
│   └── step3/        #   Simple TSP
├── controller/       # REST 엔드포인트
├── service/          # 비즈니스 로직 + 외부 API 연동
├── domain/           # JPA 엔티티 (user, band, place, vote, schedule ...)
├── dto/              # 요청/응답 객체
├── config/           # 설정 (@ConfigurationProperties, Security, WebSocket ...)
└── common/           # 공통 (보안 필터, 예외 처리)

docs/                 # 기획·알고리즘·구현 현황 문서
synctrip/mysql/       # DDL 스키마 (버전 관리)
```

---

## 📖 더 보기

- [`docs/SyncTrip_구현현황.md`](docs/SyncTrip_구현현황.md) — 기능별 구현 상태 상세
- [`docs/SyncTrip_알고리즘의사코드_v2_6.md`](docs/SyncTrip_알고리즘의사코드_v2_6.md) — 일정 생성 알고리즘 명세

---
