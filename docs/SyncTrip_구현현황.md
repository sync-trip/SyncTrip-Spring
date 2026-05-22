# SyncTrip 구현 현황 문서
**인수인계 문서 기준:** v6 | **최신 DDL:** `SyncTrip_DDL_v7.sql`

> 이 문서는 기능이 구현되거나 수정될 때마다 업데이트합니다.
> 기준: `SyncTrip_인수인계문서_v6.md` + 실제 Spring Boot 코드 (`com.sync.*`)

---

## 범례

| 표시 | 의미 |
|---|---|
| ✅ 구현 | 완전히 구현되어 동작하는 기능 |
| ⚠️ 부분 구현 | 일부만 구현되었거나 보완이 필요한 기능 |
| ❌ 미구현 | 인수인계 문서에 있지만 코드가 없는 기능 |
| ➕ 추가 구현 | 인수인계 문서에 없었으나 추가로 구현한 기능 |

---

## 1. 인증 / 회원 관리

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-001 | 회원가입/로그인 (카카오) | ✅ 구현 | 2026-05-12 | `KakaoAuthService` | JWT 발급 (access + refresh) |
| USR-001 | 회원가입/로그인 (구글) | ✅ 구현 | 2026-05-20 | `GoogleAuthService` | 구글 OAuth 추가 |
| USR-002 | 정보 수정 / 탈퇴 | ✅ 구현 | 2026-05-12 ~ 05-21 | `UserController`, `AuthService` | 프로필 조회·수정(05-21) / Soft Delete 탈퇴 / 로그아웃(05-12) |
| USR-029 | 로그아웃 / 세션 만료 | ✅ 구현 | 2026-05-12 | `AuthService.logout()` | JWT 만료 기반 / Refresh Token 갱신 |
| — | 토큰 갱신 | ➕ 추가 구현 | 2026-05-12 | `AuthService.refresh()` | Refresh Token으로 Access Token 재발급 |

**보완할 점**
- Refresh Token 블랙리스트 미구현: 로그아웃 시 서버 측 무효화 없음. 코드에 `// 향후 추가 가능` 주석 있음

---

## 2. 그룹(밴드) 관리

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-003 | 그룹 생성 (+ 숙소 입력) | ✅ 구현 | 2026-05-12 ~ 05-18 | `BandService.createBand()` | 숙소 좌표/이름 포함 생성, 수정(05-18) |
| USR-004 | 그룹 초대 / 참여 | ✅ 구현 | 2026-05-12 | `InviteController`, `BandService.joinBand()` | 초대 코드 기반 참여 |
| USR-005 | 최대 인원 제한 (8명) | ✅ 구현 | 2026-05-12 | `BandService.joinBand()` | countByBand ≥ maxMembers 시 409 |
| USR-006 | 초대 코드 재발급 | ✅ 구현 | 2026-05-12 | `BandService.getOrRefreshInviteCode()` | 만료 시 자동 재발급 |
| USR-009 | Ready 상태 전환 | ✅ 구현 | 2026-05-16 | `BandService.markReady()` | 장바구니 1개 이상 필수 / `DELETE /api/bands/{bandId}/ready` 존재하나 항상 403 반환 (취소 불가) |
| USR-014 | 투표 강제 시작/마감 | ✅ 구현 | 2026-05-16 | `BandService.advanceBandStatus()` | 방장 전용 / PLANNING→VOTING→GENERATING→TRAVELLING→DONE |
| USR-028 | 여행 종료 처리 | ⚠️ 부분 구현 | 2026-05-16 | `BandService.advanceBandStatus()` | DONE 상태 전환 가능. 종료 알림·정산 안내 플로우 없음 |
| — | 밴드 삭제 (Soft Delete) | ➕ 추가 구현 | 2026-05-12 | `BandService.deleteBand()` | `DELETE /api/bands/{bandId}` / 방장 전용 / is_deleted 마킹 |

**보완할 점**
- USR-028: 여행 DONE 전환 시 밴드 전원 알림 + 정산 유도 안내 없음
- 투표 자동 종료 (1시간 타임아웃, 전원 투표 완료 감지) 미구현 — 방장 수동 마감만 가능

---

## 3. 장소 탐색 / 장바구니

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-007 | 지도 장소 검색 | ✅ 구현 | 2026-05-19 | `KakaoPlacesService`, `GooglePlacesService` | 국내=카카오(거리순/최대15), 해외=구글(최대20) / `GET /api/bands/{bandId}/places/search` |
| USR-008 | 블라인드 장바구니 담기 | ✅ 구현 | 2026-05-14 | `PlacePickController`, `PlacePickService` | 1인당 5개 제한 / bookmark_count 동기화 |
| — | 여행지 탐색 (인기 목록 + 도시 검색) | ➕ 추가 구현 | 2026-05-19 | `DestinationController`, `DestinationService` | `GET /api/destinations/popular` (하드코딩 28개) / `GET /api/destinations/search?query=` (Google Places + Spring Cache) |

---

## 4. 투표

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-010 | 스와이프 투표 | ✅ 구현 | 2026-05-16 | `VoteService`, `VoteController` | LIKE/DISLIKE/SKIP + WebSocket 실시간 중계 |
| USR-011 | 카테고리별 순위 풀 | ✅ 구현 | 2026-05-17 | `AlgorithmService` (Step 1) | WeightedCostFunction → mainPool / altPool 분리 |
| USR-012 | Density 기반 슬롯 편입 | ✅ 구현 | 2026-05-17 | `AlgorithmService` (Step 1→2) | RELAXED/PACKED 모드별 Density 쿼터 |
| USR-013 | 최종 결과 확인 | ✅ 구현 | 2026-05-16 | `VoteController` | 투표 결과 조회 API |

---

## 5. 알고리즘 (순수 함수)

| 단계 | 기능 | 상태 | 구현일 | 구현 위치 |
|---|---|---|---|---|
| Step 1 | Weighted Cost Function | ✅ 구현 | 2026-05-17 | `algorithm/step1/WeightedCostFunction.java` |
| Step 2 | K-Means Clustering | ✅ 구현 | 2026-05-17 | `algorithm/step2/KMeansClustering.java` |
| Step 3 | Simple Order & Time TSP | ✅ 구현 | 2026-05-17 | `algorithm/step3/SimpleTsp.java` |
| Plan B | 폭포수 반경 검색 (Stage0: 1km, Stage1: 2km, Stage2: 3km) | ✅ 구현 | 2026-05-19 ~ 05-21 | `algorithm/planb/PlanBRecommender.java` (순수 함수) + `ScheduleService.getPlanBRecommendations()` (서비스 인라인 구현) | 최대 7개 추천 / 카테고리 동일 교체 / 영업시간 체크(해외) |
| 파이프라인 | Step1→2→3 통합 진입점 | ✅ 구현 | 2026-05-17 | `algorithm/AlgorithmService.java` |

> 알고리즘 함수 전체가 순수 함수 (DB 접근 없음). 서비스 레이어(`ScheduleService`)에서 입력 조립 후 호출.
> ⚠️ 주의: `PlanBRecommender.java`(알고리즘 패키지)는 단위 테스트에서만 사용됨. 실제 API는 `ScheduleService` 내부에 인라인 구현(`PLAN_B_MAX_RECOMMENDATIONS = 7`)으로 동작함.

---

## 6. 일정 관리

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-015 | 일자별 동선 초안 생성 | ✅ 구현 | 2026-05-17 | `ScheduleService.generateAutomated()` | 투표 종료 후 알고리즘 파이프라인 자동 실행 |
| USR-016 | 이상치 감지 / 제외 | ✅ 구현 | 2026-05-17 | `AlgorithmService` (Step 2/3) | 거리 이상치 감지 + `OUTLIER_FULL_DAY` 배지 |
| USR-017 | Drag & Drop 순서 변경 | ✅ 구현 | 2026-05-17 | `ScheduleService.reorderSchedule()` | REORDER 모드 (TSP 재계산) |
| USR-018 | 수동 편집 시 대안 팝업 (Plan B) | ✅ 구현 | 2026-05-19 | `ScheduleService.swapSchedulePlace()` | RESTRUCTURE 트랜잭션 + altPool 복귀 / 교체 후 해당 Day TSP 재계산 |
| USR-031 | 실시간 Plan B 추천 | ✅ 구현 | 2026-05-19 | `ScheduleService.getPlanBRecommendations()` | `POST /api/bands/{bandId}/schedule/plan-b` / 최대 7개 |
| — | 편집 락 (5분 타임아웃 + 자동 갱신) | ➕ 추가 구현 | 2026-05-20 ~ 05-21 | `ScheduleService.startEditing()` / `finishEditing()` | `POST /api/bands/{bandId}/schedule/edit/start·finish` / 그룹 동시 편집 방지 |
| — | 일정 변경 WebSocket 브로드캐스트 | ➕ 추가 구현 | 2026-05-19 | `ScheduleService` → `SimpMessagingTemplate` | 장소 교체 시 `/topic/bands/{bandId}/schedule` 채널로 `ScheduleUpdatedEvent` 발송 |

**보완할 점**
- 숙소 변경 시 current_date 이후 day만 재계산(Step 3 partial) 구현 여부 확인 필요

---

## 7. 가계부 / 정산

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-019 | 영수증 OCR | ✅ 구현 | 2026-05-21 | `GeminiOcrService` | Gemini Vision 1.5 Flash (인수인계: "추후 결정" → Gemini 확정) |
| USR-020 | 지출 수동 관리 | ✅ 구현 | 2026-05-21 | `ExpenseService`, `ExpenseController` | 지출 CRUD / 다통화 / 소프트삭제 |
| USR-021 | 다통화 환율 | ✅ 구현 | 2026-05-21 | `GroupFinanceService`, `ExchangeRateApiService` | ExchangeRate-API 연동 / 기준 통화 설정 |
| USR-022 | 더치페이 정산 | ✅ 구현 | 2026-05-21 | `SettlementService` | 최소 송금 횟수 그리디 알고리즘 |

---

## 8. 알림

> **주의:** 인수인계 문서 v6에는 "In-App 알림만 (FCM 미사용)"이라고 명시되어 있으나, **실제 구현에서는 FCM 푸시 알림이 추가되었습니다.** (Firebase Admin SDK 9.2.0)

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-026 | 주요 이벤트 알림 | ✅ 구현 | 2026-05-21 | `NotificationService`, `FcmService` | In-App + FCM 푸시 동시 발송 |
| USR-027 | 알림 토글 (수신 설정) | ✅ 구현 | 2026-05-21 | `NotificationService`, `NotificationController` | 타입별 FCM on/off / 조회(GET)·변경(PATCH) API |
| USR-030 | 공휴일 알림 (Nager.Date) | ❌ 미구현 | — | — | Nager.Date API 연동 없음 |

### 알림 타입별 트리거

| NotificationType | 제목 | 트리거 위치 | 상태 | 구현일 |
|---|---|---|---|---|
| `MEMBER_READY` | 멤버 준비완료 | `BandService.markReady()` | ✅ 구현 | 2026-05-21 |
| `MEMBER_JOINED` | 새 멤버 합류 | `BandService.joinBand()` | ➕ 추가 구현 | 2026-05-21 |
| `VOTE_STARTED` | 투표 시작 | `BandService.markReady()` / `advanceBandStatus()` | ✅ 구현 | 2026-05-21 |
| `SCHEDULE_UPDATED` | 일정 변경 | `ScheduleService.generateInternal()` / `swapSchedulePlace()` | ✅ 구현 | 2026-05-21 |
| `SETTLEMENT_REQUEST` | 정산 요청 | `SettlementController` → `NotificationService.requestSettlement()` | ➕ 추가 구현 | 2026-05-21 |

### 추가 구현된 알림 API (인수인계 문서에 없음)

| API | 설명 | 구현일 |
|---|---|---|
| `POST /api/users/fcm-token` | FCM 디바이스 토큰 등록 | 2026-05-21 |
| `GET /api/users/notification-settings` | 알림 수신 설정 조회 | 2026-05-21 |
| `PATCH /api/users/notification-settings` | 알림 수신 설정 변경 | 2026-05-21 |
| `GET /api/notifications?page=0&size=20` | 알림 목록 조회 (페이지네이션) | 2026-05-21 |
| `GET /api/notifications/unread-count` | 미읽음 알림 개수 | 2026-05-21 |
| `PATCH /api/notifications/{id}/read` | 알림 1건 읽음 처리 | 2026-05-21 |
| `PATCH /api/notifications/read-all` | 알림 전체 읽음 처리 | 2026-05-21 |
| `DELETE /api/notifications/{id}` | 알림 1건 삭제 | 2026-05-21 |
| `DELETE /api/notifications` | 알림 전체 삭제 | 2026-05-21 |
| `POST /api/bands/{bandId}/settlement/request` | 정산 요청 알림 발송 | 2026-05-21 |
| 오래된 알림 자동 삭제 스케줄러 | 매일 새벽 3시, 30일 이전 알림 일괄 삭제 | 2026-05-21 |

**보완할 점**
- USR-030 공휴일 알림: Nager.Date API 연동 + `@Scheduled` 스케줄러 미구현

---

## 9. 아카이빙

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-023 | 공유 앨범 | ❌ 미구현 | — | — | DDL(`album_photos` 테이블)은 있음 |
| USR-024 | 여권 스탬프 | ❌ 미구현 | — | — | DDL(`passport_stamps` 테이블)은 있음 |
| USR-025 | 과거 여행 기록 | ❌ 미구현 | — | — | `getMyBands`는 있으나 DONE 아카이브 전용 뷰 없음 |

**보완할 점**
- 3개 기능 모두 DDL만 있고 서비스/컨트롤러 없음. 졸업 프로젝트 시연 범위 확인 필요

---

## 10. 시스템 / 기타

| 기능 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|
| WebSocket (STOMP) | ✅ 구현 | 2026-05-16 | `SimpMessagingTemplate` | Ready 이벤트(`/topic/.../ready`), 밴드 상태 전환(`/topic/.../status`), 투표 이벤트(`/topic/.../votes`), 일정 변경(`/topic/.../schedule`) |
| Spring Cache (인메모리) | ➕ 추가 구현 | 2026-05-21 | `@EnableCaching` | 도시 검색 중복 호출 방지 (`destination-search` 캐시) |
| DebugController | ➕ 추가 구현 | 2026-05-21 | `DebugController` | 알림·알고리즘 수동 테스트용 엔드포인트 / `app.security.enabled=false` 조건부 활성화 |
| InviteController (딥링크 랜딩) | ➕ 추가 구현 | 2026-05-12 | `InviteController` | `GET /invite?code=...` → 딥링크 `synctrip://band/join?code=...` HTML 랜딩 페이지 / 800ms 후 자동 앱 열기 |

---

## 11. 미구현 요약 (우선순위 순)

| 우선순위 | 기능 | 관련 USR | 설명 |
|---|---|---|---|
| 🔴 높음 | 투표 자동 종료 | USR-014 | 1시간 타임아웃 또는 전원 투표 완료 시 자동 마감 스케줄러 |
| 🔴 높음 | 여행 종료 알림·플로우 | USR-028 | DONE 전환 시 밴드 전원 알림 + 정산 유도 안내 |
| 🟡 중간 | 공휴일 알림 | USR-030 | Nager.Date API 연동 + `@Scheduled` 스케줄러 |
| 🟡 중간 | Refresh Token 블랙리스트 | — | 로그아웃 후 토큰 서버 측 무효화 |
| 🟢 낮음 | 공유 앨범 | USR-023 | DDL 있음, 서비스·컨트롤러 없음 |
| 🟢 낮음 | 여권 스탬프 | USR-024 | DDL 있음, 서비스·컨트롤러 없음 |
| 🟢 낮음 | 과거 여행 아카이브 | USR-025 | DONE 상태 밴드 전용 뷰 없음 |

---

## 12. 인수인계 문서와 다르게 결정된 사항

| 항목 | 인수인계 문서 v6 | 실제 구현 | 결정일 |
|---|---|---|---|
| FCM 알림 | "In-App 알림만 (FCM 미사용)" | **FCM 푸시 알림 구현** (Firebase Admin SDK 9.2.0) | 2026-05-21 |
| Vision AI | "GPT-4o or Gemini Vision, 추후 결정" | **Gemini Vision 1.5 Flash 확정** | 2026-05-21 |
| 알림 타입 수 | 4종 | **5종** (`MEMBER_JOINED` 추가) | 2026-05-21 |
| 소셜 로그인 | 카카오 / 구글 (계획) | **카카오 + 구글 모두 구현 완료** | 2026-05-20 |
| Plan B 최대 추천 수 | §7.7 인수인계 문서 기준 불명확 | **최대 7개** (`PLAN_B_MAX_RECOMMENDATIONS = 7`) — DebugController 주석의 "최대 3개"는 오기재 | 2026-05-19 |

---

## 13. Android 클라이언트 구현 현황 (2026-05-23 기준)

| 기능 | 상태 | 구현일 | 비고 |
|---|---|---|---|
| 카카오 / 구글 로그인 | ✅ 구현 | 2026-05-22 | JWT 저장, 자동 토큰 갱신, 로그아웃·회원탈퇴 |
| FCM 푸시 알림 수신 | ✅ 구현 | 2026-05-22 | `SyncTripFirebaseService`, 채널 생성, 토큰 서버 등록 |
| 메인 화면 DrawerLayout 사이드 메뉴 | ✅ 구현 | 2026-05-23 | 내 프로필·알림·설정·로그아웃·회원탈퇴 / 프로필 이미지·이름 연동 |
| 밴드 목록 조회 / 생성 / 참여 / 삭제 | ✅ 구현 | 2026-05-22 | 초대 딥링크, 초대코드 BottomSheet UI |
| 장소 탐색 + 블라인드 장바구니 | ✅ 구현 | 2026-05-22 | 카카오(국내) / 구글(해외), 픽 목록 BottomSheet 조회·삭제 |
| 스와이프 투표 (WebSocket) | ✅ 구현 | 2026-05-22 | 실시간 진행 현황, 투표 진행 칩 상단 고정 |
| 일정 조회 / Plan B 교체 | ✅ 구현 | 2026-05-22 | 편집 락, WebSocket 실시간 반영 |
| 가계부 / 정산 UI | ❌ 미구현 | — | 백엔드 API 완료, 프론트 미착수 |
| 알림 목록 화면 | ❌ 미구현 | — | API 완료, 사이드 메뉴 "알림" 탭 연결 필요 |
| 여권 스탬프 화면 | ❌ 미구현 | — | DDL·API 없음 |
| 공유 앨범 화면 | ❌ 미구현 | — | DDL만 있음 |

---

## 변경 이력

| 날짜 | 변경 내용 |
|---|---|
| 2026-05-21 | 문서 최초 작성. 전체 기능 현황 정리 |
| 2026-05-21 | 알림 보완 (페이지네이션, 삭제 API, 설정 조회, 정산 요청, 오래된 알림 삭제 스케줄러) |
| 2026-05-21 | `MEMBER_JOINED` 알림 타입 추가, 멤버 합류 알림 연동, 오래된 알림 자동 삭제 스케줄러 추가 |
| 2026-05-22 | 코드 3회 정독 후 누락 항목 반영: DestinationController/Service, InviteController, 밴드 삭제 API, Plan B 오기재 수정, WebSocket 채널 보완 |
| 2026-05-23 | Android 클라이언트 구현 현황 섹션 추가, VOTE_STARTED 알림 방장 제외(`notifyAllExcept`) 반영 |

---

**마지막 수정:** 2026-05-22 | **최신 DDL:** `SyncTrip_DDL_v7.sql`
