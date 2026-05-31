# SyncTrip 구현 현황 문서
**인수인계 문서 기준:** v6 | **최신 DDL:** `SyncTrip_DDL_v13.sql`

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
| USR-029 | 로그아웃 / 세션 만료 | ✅ 구현 | 2026-05-12 | `AuthService.logout()` | JWT 만료 기반 / Refresh Token Redis 블랙리스트 서버 측 무효화 |
| — | 토큰 갱신 | ➕ 추가 구현 | 2026-05-12 | `AuthService.refresh()` | Refresh Token으로 Access Token 재발급 / 블랙리스트 체크 선행 |
| — | Refresh Token 블랙리스트 | ➕ 추가 구현 | 2026-05-22 | `RedisTokenBlacklistService` | 로그아웃 시 Redis에 남은 TTL만큼 저장 / `/refresh` 호출 시 블랙리스트 검증 |

**보완할 점**
- (없음)

---

## 2. 그룹(밴드) 관리

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-003 | 그룹 생성 (+ 숙소 입력) | ✅ 구현 | 2026-05-12 ~ 05-18 | `BandService.createBand()` | 숙소 좌표/이름 포함 생성, 수정(05-18) |
| — | 숙소 좌표 BandResponse 노출 | ➕ 추가 구현 | 2026-05-31 | `BandResponse.java`, `BandService.toBandResponse()` | `accommodationLat / accommodationLng` (Double nullable) 필드 추가. Android 일정 지도 숙소 핀 표시용 |
| USR-004 | 그룹 초대 / 참여 | ✅ 구현 | 2026-05-12 | `InviteController`, `BandService.joinBand()` | 초대 코드 기반 참여 |
| USR-005 | 최대 인원 제한 (8명) | ✅ 구현 | 2026-05-12 | `BandService.joinBand()` | countByBand ≥ maxMembers 시 409 |
| USR-006 | 초대 코드 재발급 | ✅ 구현 | 2026-05-12 | `BandService.getOrRefreshInviteCode()` | 만료 시 자동 재발급 |
| USR-009 | Ready 상태 전환 | ✅ 구현 | 2026-05-16 | `BandService.markReady()` | 장바구니 1개 이상 필수 / `DELETE /api/bands/{bandId}/ready` 존재하나 항상 403 반환 (취소 불가) |
| USR-014 | 투표 강제 시작/마감 | ✅ 구현 | 2026-05-16 ~ 05-23 | `BandService.advanceBandStatus()`, `VoteScheduler` | 방장 전용 강제 마감 / 전원 투표 완료 시 즉시 자동 마감 / 1시간 타임아웃 자동 마감 |
| USR-028 | 여행 종료 처리 | ✅ 구현 | 2026-05-16 ~ 05-23 | `BandService.advanceBandStatus()` | DONE 전환 시 밴드 전원에게 `TRIP_ENDED` 알림 + 정산 유도 메시지 발송 + 여권 스탬프 자동 부여 |
| — | 밴드 삭제 (Soft Delete) | ➕ 추가 구현 | 2026-05-12 | `BandService.deleteBand()` | `DELETE /api/bands/{bandId}` / 방장 전용 / is_deleted 마킹 |

**보완할 점**
- (없음)

---

## 3. 장소 탐색 / 장바구니

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-007 | 지도 장소 검색 | ✅ 구현 | 2026-05-26 | `PlaceSearchService`, `GooglePlacesService` | 국내/해외 모두 Google Places Text Search (keyword 필수, 없으면 400) / 최대 20개 / `GET /api/bands/{bandId}/places/search?keyword=&category=` |
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
| Step 3 | Simple Order & Time TSP + FOOD 시간 윈도우 끼워넣기 (FIX-47) | ✅ 구현 | 2026-05-17 → 2026-05-30 | `algorithm/step3/SimpleTsp.java` | 비FOOD NN 후 FOOD를 점심(PACKED)/저녁 윈도우에 삽입. 경고 배지 3종(mealWindowViolation/lateSchedule/openingHoursUnverified) + DAY_OVERLOADED 추가. 이동시간 최소 3분 하한 적용. |
| Plan B | `ScheduleService.getPlanBRecommendations()` — DB(ScheduleAlt) 기반 / CULTURE↔NATURE 호환 그룹 / priorityScore 정규화 | ✅ 구현 | 2026-05-19 → 2026-05-30 | `service/ScheduleService.java` | 최대 7개 / 1km→2km→3km 3단계 반경 확장 / 카테고리 호환 그룹(CULTURE↔NATURE 허용) / priorityScore [-1.0,1.4] → [0,1] 정규화 후 60/40 가중합 / 해외 밴드 영업시간 검증 포함 |
| 파이프라인 | Step1→2→3 통합 진입점 | ✅ 구현 | 2026-05-17 | `algorithm/AlgorithmService.java` |

> 알고리즘 함수 전체가 순수 함수 (DB 접근 없음). 서비스 레이어(`ScheduleService`)에서 입력 조립 후 호출.
> Plan B 추천은 DB(ScheduleAlt 테이블) 기반이므로 서비스 레이어에서 직접 구현. `algorithm/planb/` 패키지는 2026-05-30 제거됨.

---

## 6. 일정 관리

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-015 | 일자별 동선 초안 생성 | ✅ 구현 | 2026-05-17 | `ScheduleService.generateAutomated()` | 투표 종료 후 알고리즘 파이프라인 자동 실행 |
| USR-016 | 이상치 감지 / 제외 | ✅ 구현 | 2026-05-17 | `AlgorithmService` (Step 2/3) | 거리 이상치 감지 + `OUTLIER_FULL_DAY` 배지 |
| USR-017 | Drag & Drop 순서 변경 | ✅ 구현 | 2026-05-23 | `ScheduleService.reorderSchedule()` | REORDER 모드 / `PATCH /schedule/reorder` / 사용자 지정 순서 고정 후 시간만 재계산 (TSP 재정렬 없음) |
| USR-018 | 수동 편집 시 대안 팝업 (Plan B) | ✅ 구현 | 2026-05-19 | `ScheduleService.swapSchedulePlace()` | RESTRUCTURE 트랜잭션 + altPool 복귀 / 교체 후 해당 Day TSP 재계산 |
| USR-031 | 실시간 Plan B 추천 | ✅ 구현 | 2026-05-19 | `ScheduleService.getPlanBRecommendations()` | `POST /api/bands/{bandId}/schedule/plan-b` / 최대 7개 |
| — | 편집 락 (5분 타임아웃 + 자동 갱신) | ➕ 추가 구현 | 2026-05-20 ~ 05-21 | `ScheduleService.startEditing()` / `finishEditing()` | `POST /api/bands/{bandId}/schedule/edit/start·finish` / 그룹 동시 편집 방지 |
| — | 일정 변경 WebSocket 브로드캐스트 | ➕ 추가 구현 | 2026-05-19 | `ScheduleService` → `SimpMessagingTemplate` | 장소 교체 시 `/topic/bands/{bandId}/schedule` 채널로 `ScheduleUpdatedEvent` 발송 |
| — | 숙소 단독 변경 + partial TSP 재계산 | ➕ 추가 구현 | 2026-05-23 | `BandService.updateAccommodation()`, `ScheduleService.recalculateFutureDays()` | `PATCH /api/bands/{bandId}/accommodation` / 방장 전용 / VOTING·GENERATING 단계 차단 / TRAVELLING 시 오늘 이후 day TSP 재계산 (v2.4 FIX-35/36) |
| — | 숙소를 TSP 출발점으로 반영 | ➕ 추가 구현 | 2026-05-30 | `SimpleTsp`, `Step3Input`, `GroupInfo`, `ScheduleService` | 숙소 좌표가 있으면 NN TSP 시작점 = 숙소 최근접 비FOOD 장소 / 첫 슬롯 travelTimeFromPrev = 숙소→첫장소 이동시간 / null이면 현행 유지 |
| — | 경고 플래그 5종 schedules 저장·노출 | ➕ 추가 구현 | 2026-05-30 | `Schedule` 엔티티, `ScheduleSlotResponse`, `ScheduleService` | is_outlier_candidate / opening_hours_violation / meal_window_violation / late_schedule / opening_hours_unverified 컬럼 저장 / DDL v13 / swap·reorder 재계산 시 갱신(outlier 보존) |
| — | DONE 상태 편집 차단 | ➕ 추가 구현 | 2026-05-30 | `ScheduleService` | startEditing / reorderSchedule / swapSchedulePlace 진입 시 BandStatus.DONE이면 409 |
| — | joined_after_voting 멤버 읽기전용 | ➕ 추가 구현 | 2026-05-30 | `ScheduleService.requireEditPermission()` | 투표 후 합류 멤버의 편집 API 호출 시 403 |
| — | 편집자 정보 + canEdit 응답 노출 | ➕ 추가 구현 | 2026-05-30 | `ScheduleResponse`, `ScheduleService.getSchedule()` | editingUserId / editingUserName / canEdit = (status≠DONE) && (!joinedAfterVoting) && (락 없거나 본인) |

**보완할 점**
- (없음)

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
| USR-030 | 공휴일 알림 (Nager.Date) | ✅ 구현 | 2026-05-23 | `HolidayService`, `HolidayController`, `HolidayWarningScheduler` | 해외 밴드 전용 / 달력 조회·합류 알림·일정 생성 후 알림·D-7 스케줄러 |

### 알림 타입별 트리거

| NotificationType | 제목 | 트리거 위치 | 상태 | 구현일 |
|---|---|---|---|---|
| `MEMBER_READY` | 멤버 준비완료 | `BandService.markReady()` | ✅ 구현 | 2026-05-21 |
| `MEMBER_JOINED` | 새 멤버 합류 | `BandService.joinBand()` | ➕ 추가 구현 | 2026-05-21 |
| `VOTE_STARTED` | 투표 시작 | `BandService.markReady()` / `advanceBandStatus()` | ✅ 구현 | 2026-05-21 ~ 05-23 |
| `TRIP_ENDED` | 여행 종료 | `BandService.advanceBandStatus()` (TRAVELLING→DONE) | ➕ 추가 구현 | 2026-05-23 |
| `SCHEDULE_UPDATED` | 일정 변경 | `ScheduleService.generateInternal()` / `swapSchedulePlace()` | ✅ 구현 | 2026-05-21 |
| `SETTLEMENT_REQUEST` | 정산 요청 | `SettlementController` → `NotificationService.requestSettlement()` | ➕ 추가 구현 | 2026-05-21 |
| `HOLIDAY_WARNING` | 현지 공휴일 안내 | `BandService.joinBand()` / `ScheduleService.generateInternal()` / `HolidayWarningScheduler` | ➕ 추가 구현 | 2026-05-23 |

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
| `GET /api/holidays?countryCode=JP&year=2026` | 국가+연도별 공휴일 목록 조회 (달력 표시용) | 2026-05-23 |
| `GET /api/bands/{bandId}/holidays` | 밴드 여행 기간 내 공휴일 목록 조회 | 2026-05-23 |
| D-7 공휴일 알림 스케줄러 | 매일 새벽 3시 10분, 7일 뒤 시작 해외 밴드에 공휴일 알림 | 2026-05-23 |

---

## 9. 아카이빙

| USR | 기능명 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|---|
| USR-023 | 공유 앨범 | ✅ 구현 | 2026-05-23 | `AlbumService`, `AlbumController`, `AlbumPhoto` | 피드(사진+글)+지도 핀 / 6개 API / Base64 MySQL 저장 / 소프트 삭제 |
| USR-024 | 여권 스탬프 | ✅ 구현 | 2026-05-23 | `PassportStampService`, `PassportStamp`, `UserController` | DONE 전환 시 멤버 전원 자동 부여 / `GET /api/users/me/stamps` |
| USR-025 | 과거 여행 기록 | ✅ 구현 | — | 프론트 클라이언트 | `getMyBands` 응답의 `status`/`startDate`/`endDate` 기준 프론트에서 다가오는/지난 여행 분류 / 별도 백엔드 불필요 |

**보완할 점**
- 소프트 삭제된 앨범 사진 영구 정리 스케줄러 미구현 (is_deleted=true 레코드 누적) — 필요 시 `NotificationCleanupScheduler` 패턴으로 추가 가능

---

## 10. 시스템 / 기타

| 기능 | 상태 | 구현일 | 구현 위치 | 비고 |
|---|---|---|---|---|
| WebSocket (STOMP) | ✅ 구현 | 2026-05-16 | `SimpMessagingTemplate` | Ready 이벤트(`/topic/.../ready`), 밴드 상태 전환(`/topic/.../status`), 투표 이벤트(`/topic/.../votes`), 일정 변경(`/topic/.../schedule`) |
| Spring Cache (인메모리) | ➕ 추가 구현 | 2026-05-21 | `@EnableCaching` | 도시 검색 중복 호출 방지 (`destination-search` 캐시) |
| Redis (토큰 블랙리스트) | ➕ 추가 구현 | 2026-05-22 | `RedisTokenBlacklistService` | 로그아웃 시 Refresh Token TTL 기반 자동 만료 블랙리스트 / `/auth/*/logout` Request Body `{"refreshToken":"..."}` |
| DebugController | ➕ 추가 구현 | 2026-05-21 | `DebugController` | 알림·알고리즘 수동 테스트용 엔드포인트 / `app.security.enabled=false` 조건부 활성화 |
| InviteController (딥링크 랜딩) | ➕ 추가 구현 | 2026-05-12 | `InviteController` | `GET /invite?code=...` → 딥링크 `synctrip://band/join?code=...` HTML 랜딩 페이지 / 800ms 후 자동 앱 열기 |

---

## 11. 미구현 요약 (우선순위 순)

| 우선순위 | 기능 | 관련 USR | 설명 |
|---|---|---|---|
| — | (미구현 기능 없음) | — | USR-023/024/025 포함 전체 기능 구현 완료 |

---

## 12. 인수인계 문서와 다르게 결정된 사항

| 항목 | 인수인계 문서 v6 | 실제 구현 | 결정일 |
|---|---|---|---|
| FCM 알림 | "In-App 알림만 (FCM 미사용)" | **FCM 푸시 알림 구현** (Firebase Admin SDK 9.2.0) | 2026-05-21 |
| Vision AI | "GPT-4o or Gemini Vision, 추후 결정" | **Gemini Vision 1.5 Flash 확정** | 2026-05-21 |
| 알림 타입 수 | 4종 | **7종** (`MEMBER_JOINED`, `TRIP_ENDED`, `HOLIDAY_WARNING` 추가) | 2026-05-21 ~ 05-23 |
| 소셜 로그인 | 카카오 / 구글 (계획) | **카카오 + 구글 모두 구현 완료** | 2026-05-20 |
| Plan B 최대 추천 수 | §7.7 인수인계 문서 기준 불명확 | **최대 7개** (`PLAN_B_MAX_RECOMMENDATIONS = 7`) — DebugController 주석의 "최대 3개"는 오기재 | 2026-05-19 |
| 공유 앨범 사진 저장 방식 | "사진 저장 방식 미정" | **Base64 LONGTEXT MySQL 저장** — 외부 스토리지(S3 등) 없이 DB에 직접 저장 / 졸업 프로젝트 범위 고려 | 2026-05-23 |
| 과거 여행 기록(USR-025) | 별도 아카이브 뷰 API 필요 | **프론트 클라이언트에서 처리** — 기존 `GET /api/bands` 응답의 `status`/날짜 기준으로 다가오는/지난 여행 분류 / 백엔드 추가 불필요 | 2026-05-23 |
| 블라인드 장바구니 장소 검색 API | 국내 = 카카오맵 API, 해외 = Google Places API | **국내/해외 모두 Google Places Text Search 사용** — Kakao API는 rating/thumbnail 미제공으로 UX 불가. KakaoPlacesService는 장소 탐색에서 미사용(카카오 로그인은 별개). `is_overseas` 플래그는 영업시간·알고리즘에서 유지 | 2026-05-26 |

---

## 13. Android 클라이언트 구현 현황

> **2026-05-23: XML 기반 앱(SyncTrip-Android)에서 Jetpack Compose 앱(SyncTrip-kt)으로 프론트엔드 전면 재개발 시작**
> UI 품질 개선 목적. 기존 XML 앱의 API 연동 로직을 Compose 아키텍처로 재구성.

### 구 프론트 (SyncTrip-Android / XML) — 참고용

| 기능 | 상태 | 비고 |
|---|---|---|
| 카카오 / 구글 로그인 | ✅ 완성 | JWT 저장·갱신·로그아웃 |
| 밴드 목록 / 생성 / 참여 | ✅ 완성 | |
| 장소 탐색 + 블라인드 장바구니 | ✅ 완성 | |
| 스와이프 투표 (WebSocket) | ✅ 완성 | |
| 일정 조회 / Plan B 교체 | ✅ 완성 | |
| 가계부 / 정산 UI | ❌ 미구현 | |

### 신 프론트 (SyncTrip-kt / Jetpack Compose) — 현재 개발 중

| 기능 | 상태 | 구현일 | 비고 |
|---|---|---|---|
| 의존성 세팅 | ✅ 구현 | 2026-05-23 | Compose BOM, Retrofit, Navigation, Coil, Kakao/Google SDK, DataStore 등 |
| 패키지 구조 | ✅ 구현 | 2026-05-23 | core / data/repository / ui/viewmodel 레이어 구성 |
| 스플래시 화면 | ✅ 구현 | 2026-05-23 | 페이드인 + 로고 펄스 애니메이션 |
| 로그인 화면 UI | ✅ 구현 | 2026-05-23 | 카카오 / 구글 / 이메일 버튼 |
| 카카오 로그인 API 연동 | ✅ 구현 | 2026-05-23 | KakaoAuthManager → 서버 JWT 발급 · DataStore 저장 확인 |
| 홈 화면 UI | ✅ 구현 | 2026-05-23 | 상단바 / 바텀 네비 / FAB / 밴드 카드 목록 |
| NavGraph 라우팅 | ✅ 구현 | 2026-05-23 | splash→login→home→밴드/장소/투표/일정/여권/알림 |
| ViewModel (Auth/Band/Vote) | ✅ 구현 | 2026-05-23 | StateFlow 기반 / Repository 패턴 |
| 밴드 생성 / 참여 화면 UI | ✅ 구현 | 2026-05-23 | UI만, ViewModel 연결 미완 |
| 장소 검색 화면 UI | ✅ 구현 | 2026-05-23 | UI만, ViewModel 연결 미완 |
| 투표 화면 UI | ⚠️ 부분 구현 | 2026-05-23 | 카드 탭 방식 구현됨. 스와이프 제스처 미구현 |
| 일정 화면 UI | ✅ 구현 | 2026-05-23 | UI만 |
| 정산 화면 UI | ✅ 구현 | 2026-05-23 | UI만 |
| NavGraph ↔ ViewModel 연결 | ❌ 미구현 | — | 현재 NavGraph가 더미 데이터 직접 전달 |
| 스와이프 투표 제스처 | ❌ 미구현 | — | VoteViewModel.swipe() 준비됨, 화면 미구현 |
| 앱 시작 시 자동 로그인 | ❌ 미구현 | — | DataStore 토큰 복구 로직 미연결 |
| FCM 알림 | ❌ 미구현 | — | 구 앱에는 있음 |

---

## 변경 이력

| 날짜 | 변경 내용 |
|---|---|
| 2026-05-21 | 문서 최초 작성. 전체 기능 현황 정리 |
| 2026-05-21 | 알림 보완 (페이지네이션, 삭제 API, 설정 조회, 정산 요청, 오래된 알림 삭제 스케줄러) |
| 2026-05-21 | `MEMBER_JOINED` 알림 타입 추가, 멤버 합류 알림 연동, 오래된 알림 자동 삭제 스케줄러 추가 |
| 2026-05-22 | 코드 3회 정독 후 누락 항목 반영: DestinationController/Service(인기 여행지·도시 검색), InviteController(딥링크 랜딩), 밴드 삭제 API, Plan B 최대 7개 오기재 수정, WebSocket 채널 전체 목록 보완, ScheduleService 편집 락 API 상세화, PlanBRecommender 실사용 여부 주석 |
| 2026-05-22 | Redis Refresh Token 블랙리스트 구현: `RedisTokenBlacklistService`, `logout()` 무효화 로직, `refresh()` 블랙리스트 체크, compose.yml Redis 서비스 추가 |
| 2026-05-22 | 탈퇴 후 재가입 버그 수정: soft delete 계정 재가입 시 DUPLICATE KEY 오류 → 계정 재활성화(`User.reactivate()`)로 처리 |
| 2026-05-22 | 탈퇴 회원 하드 삭제 스케줄러 추가: `UserPurgeScheduler` / `APP_USER_PURGE_ENABLED=true` + `THRESHOLD_SECONDS=30` 설정 시 30초 뒤 완전 삭제 |
| 2026-05-23 | `PlaceCategory`에서 잘못 추가된 `LODGING` 값 제거 |
| 2026-05-23 | `UserPurgeScheduler` 버그 수정: 소프트 삭제된 그룹의 `owner_id` 참조가 남아 `users` 삭제 시 FK 제약 오류 발생 → 하드 삭제 전 소프트 삭제 그룹 레코드 정리 추가 |
| 2026-05-23 | Android 클라이언트 Jetpack Compose(SyncTrip-kt)로 전면 재개발 시작. 의존성·패키지구조·화면 UI·카카오 로그인 연동 완료 |
| 2026-05-23 | Android 클라이언트 구현 현황 섹션 추가, VOTE_STARTED 알림 방장 제외(`notifyAllExcept`) 반영 |
| 2026-05-23 | USR-028 DONE 전환 알림 구현, 투표 자동 종료(전원 완료 즉시 + 1시간 타임아웃), VOTE_STARTED 강제시작 시 방장 제외, TRIP_ENDED 알림 타입 추가, Refresh Token 블랙리스트 구현 현황 반영 |
| 2026-05-23 | USR-030 공휴일 알림 구현: HolidayService(Nager.Date API+캐싱), 달력 조회 API, 밴드 공휴일 조회 API, 합류/일정 생성 시 알림, D-7 스케줄러, DDL v8(notifications ENUM 확장) |
| 2026-05-23 | USR-023 공유 앨범 구현: AlbumPhoto 엔티티, AlbumService(6개 메서드), AlbumController(6개 API), DDL v9(photo_url→photo_data LONGTEXT), DDL v10(caption/latitude/longitude 추가) |
| 2026-05-23 | USR-024 여권 스탬프 구현: PassportStamp 엔티티, PassportStampService, GET /api/users/me/stamps, BandService DONE 전환 시 stampForAllMembers() 자동 호출 |
| 2026-05-23 | 구현현황 문서 전면 재검증 및 갱신: 아카이빙 섹션 ❌→✅, 미구현 요약 갱신, 결정사항 추가, DDL v10 반영 |
| 2026-05-23 | USR-017 Drag & Drop 순서 변경 실제 구현: `ScheduleService.reorderSchedule()` / `PATCH /schedule/reorder` |
| 2026-05-23 | 숙소 변경 + TRAVELLING 단계 partial TSP 재계산 구현: `Band.updateAccommodation()` / `BandService.updateAccommodation()` / `ScheduleService.recalculateFutureDays()` / `PATCH /api/bands/{bandId}/accommodation` (v2.4 FIX-35/36) |
| 2026-05-24 | ➕ 밴드 썸네일 저장 구현: `Band.thumbnailUrl` 필드 추가, `BandCreateRequest.thumbnailUrl` 수신, `BandResponse.thumbnailUrl` 반환. DDL v11(`user_groups.thumbnail_url` 컬럼 추가). Android 홈 화면 밴드 카드 이미지 표시 연동. |
| 2026-05-26 | 타입 불일치 수정: `user_groups.thumbnail_url` VARCHAR(500) → TEXT. Google Places 사진 URL 500자 초과 시 strict mode 오류 방지. `Band.java` `@Column` 동기화. DDL v12. |
| 2026-05-26 | 블라인드 장바구니 국내 장소 검색 Google 통일: `PlaceSearchService.searchDomestic()` 제거 → `searchWithGoogle()`으로 통합. keyword 없으면 BAD_REQUEST(국내/해외 동일). Android `PassportAndSearchScreens.kt` 버튼 "Google 지도에서 보기" → "지도에서 보기". `PlaceSearchServiceTest` 갱신. |
| 2026-05-26 | 죽은 코드 및 주석 정리: `radiusMeters` 파라미터 전체 제거(`PlaceController`, `PlaceSearchService`, `PlaceSearchServiceTest`, Android `SyncTripApiService`, `BandRepository`). `DEFAULT_RADIUS_METERS` 상수 제거. `KakaoProperties`, `KakaoLocalSearchResponse`, `PlaceSearchResult` 주석 최신화. CLAUDE.md 절대 규칙 5 수정("국내는 opening_hours=NULL" → isOverseas 기반 빈 맵 전달로 정정). Android `NavGraph.kt` `onSearch` / `onCategoryChange` keyword 빈 값 가드 추가. |
| 2026-05-26 | VoteScheduler 반복 실패 수정: 장바구니 없는 밴드가 VOTING 상태일 때 1분마다 마감 실패 로그 반복 → 실패 시 `BandService.rollbackVotingToPlanning()`으로 PLANNING 복원. `Band.rollbackToPlanning()` 추가. `VoteService.java` `Comparator` import 누락 버그 수정. |
| 2026-05-26 | FCM data 페이로드 추가 → 멤버 합류·장바구니 변경 즉시 반영: `FcmService.send()` data 오버로드 추가, `NotificationService.buildData()` bandId+type 전달. Android `SyncTripApplication` bandRefreshFlow SharedFlow 추가, `SyncTripFirebaseService` data 수신 시 emitBandRefresh, `NavGraph` tripLobby에서 collect해 loadMembers/loadPicks 즉시 호출. |
| 2026-05-30 | 알고리즘 코드 리뷰 반영 6개 항목 구현: (1) FOOD 시간 윈도우 끼워넣기 FIX-47 — 비FOOD NN 후 PACKED:점심+저녁/RELAXED:저녁 윈도우에 FOOD 삽입; (2) 경고 배지 3종 — mealWindowViolation/lateSchedule/openingHoursUnverified; (3) DAY_OVERLOADED 경고 — 마지막 슬롯 endTime > 22:00; (4) 이동시간 MIN_TRAVEL_MINUTES=3 하한; (5) PlanB priorityScore [-1.0,1.4]→[0,1] 정규화; (6) PlanB CULTURE↔NATURE 호환 그룹 + 2km fallback. |
| 2026-05-30 | PlanB 이중 구현 통합: `algorithm/planb/` 패키지 전체 삭제(PlanBRecommender/PlanBInput/PlanBResult/PlanBCandidate), `ScheduleService.getPlanBRecommendations()`에 점수 정규화·CULTURE↔NATURE 카테고리 호환 반영. 관련 테스트(PlanBRecommenderTest, TokyoTripScenarioTest PlanB 케이스, TokyoTripResultOutputTest 표5) 정리. |
| 2026-05-30 | UI/UX 백엔드 보완 5건: ①숙소를 TSP 출발점으로 반영(GroupInfo/Step3Input/SimpleTsp/ScheduleService) ②경고 플래그 5종 schedules 컬럼 저장·노출(DDL v13, Schedule 엔티티, ScheduleSlotResponse) ③DONE 상태 편집 차단(409) ④joined_after_voting 편집 금지(403) ⑤getSchedule 응답에 editingUserId/editingUserName/canEdit 추가. |
| 2026-05-31 | `BandResponse`에 숙소 좌표 추가: `BandResponse.java` record에 `Double accommodationLat / accommodationLng` (nullable) 추가. `BandService.toBandResponse()`에서 `band.getAccommodationLat() / getAccommodationLng()` 반환. Android 일정 지도 숙소 핀 + 로비 "지도에서 보기" 버튼 연동 목적. DDL 변경 없음(Band 엔티티에 이미 컬럼 존재). |

---

**마지막 수정:** 2026-05-31 (BandResponse 숙소 좌표 추가) | **최신 DDL:** `SyncTrip_DDL_v13.sql`
