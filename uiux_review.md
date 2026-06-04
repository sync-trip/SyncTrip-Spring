# SyncTrip UI/UX 미결정 정책 · 구현 공백 분석

> 분석 기준: 실제 사용자 시나리오를 따라가며 "이 시점에 무슨 일이 일어나야 하는가"를 먼저 정의하고,
> 코드(파일명:라인)로 현재 상태를 검증했다. 코드 수정 없이 분석만 수행.
> 분석일: 2026-05-30

---

## [시나리오 A] 일정 생성 직후 첫 화면

### 이 시점에 무슨 일이 일어나야 하는가
투표가 마감되면 `BandService.finishVotingInternal()`(BandService.java:256)이 `scheduleService.generateAutomated()`를 호출하고,
`/topic/bands/{bandId}/status`로 `StatusEvent`를 쏜다(BandService.java:262). 동시에 `SCHEDULE_UPDATED` FCM 알림이 전원에게 간다(ScheduleService.java:236).
사용자가 일정 화면을 열면 `GET /api/bands/{bandId}/schedule`(ScheduleController.java:36)로 `ScheduleResponse`를 받는다.

### 핵심 발견 — 알고리즘 경고 플래그가 프론트까지 전달되지 않는다
`ScheduledPlace`(step3/ScheduledPlace.java:7)는 `isOutlierCandidate`, `openingHoursViolation`, `mealWindowViolation`,
`lateSchedule`, `openingHoursUnverified` 5개 플래그를 **계산해서 들고 있다**. 그런데:
- `saveSchedules()`(ScheduleService.java:250-269)는 band/place/day/order/startTime/duration/travelTime만 저장하고 **5개 플래그를 전부 버린다**.
- `Schedule` 엔티티(Schedule.java:20-56)에 해당 컬럼이 없다.
- `ScheduleSlotResponse`(ScheduleSlotResponse.java:5-12)에도 해당 필드가 없다.

즉 알고리즘이 "이 장소는 동떨어졌다 / 영업시간 위반 / 식사시간 어긋남 / 22시 이후 / 영업시간 미확인"을 판정해도
**프론트는 그 정보를 받을 방법이 전혀 없다.**

| 번호 | 질문 | 코드 상태 | 미결정 시 문제 | 권장 방향 |
|---|---|---|---|---|
| A-1 | `isOutlierCandidate=true`인 장소 카드에 어떤 배지를 보여줄지 | **구현 공백** (DB·DTO에 플래그 없음, ScheduledPlace.java:18 → 저장 안 됨) | 프론트가 배지를 띄우고 싶어도 데이터가 없어 구현 불가 | `schedules` 테이블에 플래그 컬럼 추가 + `ScheduleSlotResponse`에 노출. 그 다음 배지 디자인 결정 |
| A-2 | 영업시간 위반/22시 이후 슬롯 경고 표시 | **구현 공백** (동일하게 4개 위반 플래그 미저장) | 해외 여행에서 "방문 시 폐점" 슬롯을 사용자가 모른 채 출발 | A-1과 같은 컬럼 확장으로 동시 해결 |
| A-3 | 빈 Day(슬롯 0개)일 때 무엇을 보여줄지 | **미정의** (getSchedule, ScheduleService.java:333-349) | `byDay` groupingBy는 저장된 슬롯만 묶으므로 **빈 Day는 응답에서 통째로 사라진다**. 프론트는 그 날이 존재하는지조차 모름 | 응답에 startDate~endDate가 있으니(ScheduleResponse.java:6) 프론트가 전체 날짜를 역산해 빈 Day 탭을 생성하고 "장소 추가 유도"를 띄울지 정책 결정. 또는 백엔드가 빈 Day도 빈 슬롯 배열로 내려주도록 변경 |
| A-4 | 생성 완료 트리거를 무엇으로 받을지 | 부분 정의 (StatusEvent + FCM 둘 다 발행, BandService.java:262 / ScheduleService.java:236) | WebSocket 미연결 사용자는 FCM만, 둘 다 받으면 중복 갱신. GENERATING→TRAVELLING 전환 폴링 여부 미정 | 프론트가 status 토픽 구독 + 앱 포그라운드 복귀 시 GET 재호출을 기본으로. 명문화 필요 |
| A-5 | "예상 동선 가이드입니다" 온보딩을 1회/매번 어느 쪽으로 | **미정의** (백엔드에 노출 이력 플래그 없음) | 매번 띄우면 거슬리고, 안 띄우면 자동생성 결과를 확정으로 오해 | 클라이언트 로컬 1회 표시로 충분(백엔드 불필요). 정책만 확정 |

### 가장 먼저 결정해야 할 것
**A-1/A-2: 알고리즘 경고 플래그(5종)를 `schedules` 테이블과 `ScheduleSlotResponse`에 노출할지.**
이걸 결정하지 않으면 "왜 이 장소가 여기 있지?"에 대한 설명을 프론트가 영영 못 한다. DDL 변경이 필요하므로 가장 무겁다.

---

## [시나리오 B] Drag & Drop 순서 변경 (REORDER)

### 이 시점에 무슨 일이 일어나야 하는가
편집 흐름은 `POST .../edit/start`(ScheduleController.java:76) → `PATCH .../reorder`(ScheduleController.java:58) → `POST .../edit/finish`(ScheduleController.java:84).
`reorderSchedule()`(ScheduleService.java:489)은 `requireEditingLock()`(ScheduleService.java:493)을 먼저 걸고,
사용자 지정 순서대로 시간만 재계산(`assignTimesInOrder`, TSP 재정렬 없음)한 뒤 즉시 `saveAll`하고
`/topic/bands/{bandId}/schedule`로 `ScheduleUpdatedEvent(bandId, userId)`를 브로드캐스트한다(ScheduleService.java:523).

### 핵심 발견 — "5분 자동저장"은 코드에 없다
인수인계상 "5분 inactivity 자동저장 후 락 해제"가 있다고 보이지만, 코드에는 **자동저장 로직도, 자동저장 스케줄러도 없다.**
`isEditingByOther()`(Band.java:261-267)가 `lastEditingAt`이 5분 지났으면 **lazy하게 락을 무효 처리**할 뿐이다.
변경분은 `PATCH /reorder`가 호출될 때만 저장되고, `finishEditing`(Band.java:256)은 명시 호출로만 락을 푼다.
즉 "드래그만 하고 저장 안 한 상태"는 5분 뒤 다른 사람이 락을 가져가도 **아무것도 저장되지 않고 사라진다.**

| 번호 | 질문 | 코드 상태 | 미결정 시 문제 | 권장 방향 |
|---|---|---|---|---|
| B-1 | 편집 락을 드래그 시작 순간 거는가, 저장 버튼에서 거는가 | **미정의** (백엔드는 reorder 호출 시점에만 락 검증, ScheduleService.java:493) | 락을 늦게 걸면 두 멤버가 동시에 드래그 시작 → 한 명이 `PATCH` 시 409 발생, 그 사이 작업 손실 | 드래그 시작(또는 화면 진입) 즉시 `edit/start` 호출하도록 프론트 규약 고정 |
| B-2 | 다른 멤버가 편집 중일 때 같은 Day 화면에 무엇을 보여줄지 | **미정의** (백엔드는 `currentlyEditingUserId`만 보관, Band.java:97. 조회 API 없음) | 누가 편집 중인지 알 방법이 없어 (b)"○○님이 편집 중" 오버레이를 못 만든다 | `GET /schedule` 응답 또는 별도 필드로 `currentlyEditingUserId`/이름을 내려줘야 함. 그 후 오버레이 vs 드래그 비활성화 결정 |
| B-3 | 5분 락 만료 시 편집 중이던 사용자에게 "자동 저장됐어요" 알림 | **구현 공백** (자동저장 자체가 없음, Band.java:261) | 사용자는 저장됐다고 믿지만 실제론 미저장 분이 날아감 | (1) reorder를 드래그 종료마다 즉시 저장하는 현재 구조를 명문화하거나 (2) 진짜 자동저장이 필요하면 별도 구현. UX 문구는 그 다음 |
| B-4 | REORDER 후 다른 멤버 화면이 자동 갱신되는가 | 부분 정의 (`ScheduleUpdatedEvent`에 **변경 데이터 없음**, ws/ScheduleUpdatedEvent = bandId+userId만, ScheduleService.java:525) | 이벤트만으로는 갱신 불가 → 반드시 `GET /schedule` 재호출 필요. 자동 re-fetch vs 수동 새로고침 미정 | 이벤트 수신 시 자동 re-fetch를 기본으로. 본인이 보낸 이벤트(userId 일치)는 무시하도록 규약화 |
| B-5 | 드래그 중 이동시간이 실시간 갱신되는가 | **미정의** (백엔드는 저장 후에만 travelTime 재계산, assignTimesInOrder ScheduleService.java:535) | 드래그 중 미리보기를 원하면 프론트가 동일한 haversine/속도 계산을 자체 구현해야 함(AlgorithmConstants.TRAVEL_SPEED_KMH) | "저장 후 갱신"을 기본으로. 실시간 미리보기는 v2 과제로 분리 |

### 가장 먼저 결정해야 할 것
**B-2: 편집자 정보(`currentlyEditingUserId`)를 조회 응답에 노출할지.**
이게 없으면 "○○님이 편집 중" 류의 동시 편집 UX를 아예 만들 수 없고, 두 멤버가 충돌하는 상황에서 사용자는 영문 모를 409만 본다.

---

## [시나리오 C] Plan B (슬롯 교체)

### 이 시점에 무슨 일이 일어나야 하는가
슬롯 롱프레스 → `POST .../plan-b`(ScheduleController.java:50)로 `getPlanBRecommendations()`(ScheduleService.java:367) 호출.
1→2→3km 단계 확장으로 최대 7개 후보를 `PlanBResponse` 리스트로 받는다.
후보 탭 → `POST .../swap`(ScheduleController.java:67) → `swapSchedulePlace()`(ScheduleService.java:628)가
슬롯에 새 장소, altPool에 기존 장소(Pool Swap)를 넣고 해당 Day TSP를 재계산(ScheduleService.java:659)한다.

### 핵심 발견 1 — 지도 검색으로 가져온 새 장소는 교체할 수 없다
`swapSchedulePlace()`는 새 장소가 `ScheduleAlt`(예비목록)에 있는지 검증하고, 없으면
**400 "예비 목록에 없는 장소는 교체할 수 없습니다"**(ScheduleService.java:647-648)를 던진다.
즉 "[지도 검색]으로 새 장소를 가져와 교체"하는 경로는 **백엔드에 존재하지 않는다.**

### 핵심 발견 2 — CULTURE↔NATURE 호환은 반영됨
`isPlanBCompatibleCategory()`(ScheduleService.java:739-742)와 `PLAN_B_CULTURE_NATURE_GROUP`(ScheduleService.java:79)로
이번 세션 반영 확인됨. 단 응답에는 호환 후보를 구분하는 라벨 필드가 없다(category만 존재).

| 번호 | 질문 | 코드 상태 | 미결정 시 문제 | 권장 방향 |
|---|---|---|---|---|
| C-1 | 후보 탭 시 즉시 교체 vs "○○으로 교체할까요?" 확인 | **미정의** (백엔드는 `POST /swap` 단발, ScheduleController.java:67) | 즉시 교체면 오탭으로 TSP가 재계산되고 전원 알림이 나감(ScheduleService.java:669) | 확인 팝업을 기본으로. swap이 비가역적 비용(알림+재계산)을 동반하므로 |
| C-2 | CULTURE↔NATURE 호환 후보를 "유사 카테고리"로 구분 표시 | 부분 정의 (호환 로직은 있으나 `PlanBResponse`에 구분 플래그 없음, PlanBResponse.java:14-32) | 사용자가 박물관 자리에 공원이 추천된 이유를 모름 | 프론트가 `category != target.category`로 자체 판별 가능. 명확히 하려면 `isCrossCategory` 플래그 추가 |
| C-3 | fallbackLevel(0/1/2)을 "1km N개·더 먼 N개"로 구분 표시 | **정의됨** (PlanBResponse에 `fallbackLevel`, `searchRadiusKmUsed`, `distanceKmToTarget` 모두 존재, PlanBResponse.java:23-27) | — (데이터 충분, 표시 방식만 프론트 재량) | 단계별 섹션 헤더로 그룹핑 권장 |
| C-4 | Pool Swap으로 복귀한 장소를 다시 볼 UI | **정의됨** (`GET .../alts`, ScheduleController.java:43 → getScheduleAlts) | — (altPool 재조회 + 재교체 모두 백엔드 지원) | "예비 장소" 탭/시트에서 alts를 노출하면 복귀 장소 재사용 가능 |
| C-5 | [지도 검색] 새 장소 교체 시 densityPoint/estimatedDuration 결정 | **구현 공백** (지도 검색 장소 교체 경로 자체가 없음, ScheduleService.java:647) | "더 나은 장소를 직접 찾아 넣기"가 불가능 — Plan B는 기존 장바구니/예비목록 안에서만 동작 | 신규 장소 교체를 지원하려면 (1) 해당 장소를 Place+ScheduleAlt로 먼저 등록하는 경로, (2) 카테고리 자동매핑 후 duration 기본값 정책이 필요 |
| C-6 | TSP 재계산 중 로딩 표시 | **미정의** (swap은 동기 처리 후 `200 Void` 반환, ScheduleController.java:72. 갱신된 일정은 응답에 없음) | 응답 본문이 비어 있어 프론트는 `GET /schedule`을 다시 호출해야 함. 그 사이 로딩 표시 정책 없음 | swap 응답을 받을 때까지 슬롯 카드 비활성화 + 스피너, 완료 후 GET 재조회 |

### 가장 먼저 결정해야 할 것
**C-5: Plan B의 범위를 "예비목록 내 교체"로 확정할지, "지도 검색 신규 장소 교체"까지 넓힐지.**
현재 코드는 전자만 지원한다. 후자를 원하면 신규 Place 등록·카테고리 매핑·duration 기본값까지 연쇄 설계가 필요하다.

---

## [시나리오 D] 여행 중 (TRAVELLING)

### 이 시점에 무슨 일이 일어나야 하는가
방장이 숙소를 바꾸면 `PATCH .../accommodation`(BandController.java:85) → `updateAccommodation()`(BandService.java:175)이
Band를 갱신하고, TRAVELLING이면 `recalculateFutureDays()`(ScheduleService.java:564)가 오늘 이후 Day의 TSP를 다시 돌린 뒤
`/topic/bands/{bandId}/schedule`로 이벤트를 쏜다(ScheduleService.java:593).

### 핵심 발견 1 — 숙소 좌표가 알고리즘/TSP에 전혀 들어가지 않는다
`generateInternal()`은 `GroupInfo`를 만들 때 **`destinationLat/Lng`(도시 좌표)만 넘기고 `accommodationLat/Lng`는 넘기지 않는다**(ScheduleService.java:162-166).
`GroupInfo`(GroupInfo.java:6-14)에 숙소 필드 자체가 없다. `recalculateDayTsp()`(ScheduleService.java:673-723)도 숙소 좌표를 쓰지 않고,
`SimpleTsp`의 출발점은 **첫 장소(`places.get(0)`)**다(SimpleTsp.java:196).
→ 결과적으로 **숙소를 바꿔도 동선이 바뀌지 않을 가능성이 매우 크다.** `recalculateFutureDays`는 돌지만 입력에 숙소가 없으니 같은 순서가 나온다.

### 핵심 발견 2 — DONE 상태에서도 일정 수정이 막히지 않는다
`reorderSchedule`/`swapSchedulePlace`/`startEditing` 어디에도 `BandStatus` 검증이 없다(ScheduleService.java:489, 628, 602 — `requireEditingLock`만 검사).
"DONE이면 수정 불가"라는 전제는 **코드에 구현되어 있지 않다.** 락만 잡으면 DONE 상태에서도 편집된다.

### 핵심 발견 3 — joined_after_voting 멤버의 일정 조회 제한이 없다
`getSchedule()`(ScheduleService.java:321)은 `requireMembership`만 검사한다.
`joinedAfterVoting=true` 멤버 차단은 **`markReady`에만**(BandService.java:305) 있고 일정 조회·편집에는 없다.
→ 투표 후 합류 멤버도 전체 일정을 보고 **편집까지 시도할 수 있다**(읽기전용 강제 없음).

| 번호 | 질문 | 코드 상태 | 미결정 시 문제 | 권장 방향 |
|---|---|---|---|---|
| D-1 | 숙소 변경 시 "오늘 이후 재계산" 확인 팝업, 방장만/전체 | **미정의** + 숙소 미반영 (BandService.java:183, GroupInfo.java) | 멤버는 `PATCH /accommodation`을 못 부름(loadBandForOwner, BandService.java:177)→ 멤버에겐 숙소 변경 UI 자체가 없음. 게다가 재계산해도 동선이 안 바뀜 | 먼저 **숙소를 TSP 출발점으로 반영할지** 결정(핵심 발견 1). 그 후 방장 전용 확인 팝업 정책 |
| D-2 | 재계산된 일정이 보고 있던 멤버 화면에 자동 갱신/깜빡임 | 부분 정의 (이벤트는 `userId=null`로 전원 발행, ScheduleService.java:595. 데이터는 없음) | B-4와 동일 — re-fetch 필요. 전체 GET 재조회 시 화면 깜빡임 우려 | day 단위 부분 갱신 또는 디핑으로 깜빡임 최소화 |
| D-3 | TRAVELLING 중 합류 멤버(joined_after_voting)가 일정 화면에서 보는 것 | **구현 공백** (조회·편집에 제한 없음, ScheduleService.java:321/489) | 후합류 멤버가 일정을 편집해 기존 멤버 동선을 바꿀 수 있음 | joined_after_voting 멤버는 일정을 **읽기전용**으로 강제(서비스단 가드 + 응답에 `canEdit` 플래그) |
| D-4 | 오전 일정을 통째로 건너뛰고 싶을 때의 액션 | **구현 공백** (슬롯 삭제·스킵 API 없음. ScheduleController에 generate/get/alts/plan-b/reorder/swap/edit만) | `is_free_time` 컬럼은 있으나(Schedule.java:41) 설정 API가 없어 "건너뛰기/삭제"가 불가. reorder로 뒤로 미루거나 swap만 가능 | 슬롯 삭제 또는 `is_free_time` 토글 엔드포인트 신설 여부 결정 |
| D-5 | DONE 전환 시 "이제 수정 불가" 안내 + 실제 차단 | **구현 공백** (상태 가드 없음, ScheduleService.java:489/628) | 안내 문구도 없고 실제로도 안 막힘 → "끝난 여행"을 누군가 바꿀 수 있음 | 편집 계열 API에 `BandStatus==DONE` 차단 추가 + 프론트 안내 |

### 가장 먼저 결정해야 할 것
**D-1(핵심 발견 1): 숙소 좌표를 알고리즘 출발점으로 반영할지.**
지금은 입력·재계산 어디에도 숙소가 들어가지 않아, "숙소 변경 → 동선 재계산" 기능 전체가 사실상 작동하지 않는다. 시나리오 E의 전제와도 직결된다.

---

## [시나리오 E] 숙소 관련 UX

### 이 시점에 무슨 일이 일어나야 하는가
`createBand()`(BandService.java:76)는 `accommodationName/Lat/Lng`를 선택값으로 받아 저장한다(BandCreateRequest).
숙소 변경은 PLANNING에서 `updateBand`/`updateInfo`, PLANNING·TRAVELLING·DONE에서 `updateAccommodation`(Band.java:238)으로 가능(VOTING/GENERATING은 차단).

### 핵심 발견 — "숙소 미입력 시 도시 중앙 출발"이라는 전제가 코드와 다르다
질문은 "숙소 미입력 시 도시 중앙 좌표가 출발점"을 전제하지만, 실제로는 **숙소를 입력하든 안 하든 출발점은 동일하다.**
앞서 본 대로 알고리즘에는 숙소가 안 들어가고(ScheduleService.java:162-166), TSP 출발점은 첫 장소다(SimpleTsp.java:196).
도시 좌표(`destinationLat/Lng`)는 Step2 K-Means 클러스터링 등에 쓰일 뿐 TSP 시작점이 아니다.
→ "숙소 미입력 — 도시 중심에서 출발" 안내를 띄우면 **사실과 다른 안내**가 된다.

| 번호 | 질문 | 코드 상태 | 미결정 시 문제 | 권장 방향 |
|---|---|---|---|---|
| E-1 | 숙소 미입력 시 "도시 중심에서 출발" 안내 | **미정의** + 전제 오류 (숙소가 알고리즘에 미반영, GroupInfo.java) | 실제 출발점은 첫 장소이므로 안내가 거짓이 됨. 사용자 혼란 | 먼저 숙소 반영 여부(D-1) 확정. 반영하면 "숙소에서 출발", 안 하면 안내 자체를 빼거나 정확히 수정 |
| E-2 | 숙소 입력 UI 위치 (생성화면/설정/일정화면 버튼) | **미정의** (백엔드는 세 경로 모두 지원: createBand BandService.java:84, updateBand:160, updateAccommodation:179) | 진입점이 흩어져 사용자가 어디서 바꾸는지 모름 | 생성화면 선택항목 + 일정화면 "숙소" 버튼 둘 다 두되, 단계별 가능 여부(VOTING/GENERATING 불가) 표시 |
| E-3 | 숙소를 이름만 표시할지 지도 핀까지 표시할지 | 부분 정의 (`accommodationName/Lat/Lng` 모두 저장됨, Band.java:78-85. 단 `BandResponse`는 name만 노출, BandService.java:357) | 좌표는 DB에 있으나 응답에 없어 프론트가 핀을 못 찍음 | `BandResponse`에 숙소 위경도 추가 후 지도 핀 표시 |
| E-4 | 방장 아닌 멤버가 숙소 정보를 보는 위치 | 부분 정의 (조회는 `BandResponse.accommodationName`만, 변경은 방장 전용 BandService.java:177) | 멤버는 숙소명만 보고 위치는 못 봄(E-3과 연결) | 멤버용 읽기전용 숙소 카드(이름+핀) 제공 |
| E-5 | Day별 다중 숙소(예: 3일차 다른 호텔) 대응 | **구현 공백** (Band에 숙소 필드 단일 1세트뿐, Band.java:78-85) | 멀티 숙소 여행을 표현할 방법이 없음 | v1은 "여행 전체 1숙소"로 명시하고, 사용자가 원하면 별도 안내(현재로선 대표 숙소 1곳 입력)로 한정. 멀티 숙소는 v2 |

### 가장 먼저 결정해야 할 것
**E-1/E-3: 숙소 좌표를 (1) 알고리즘 출발점으로 쓸지, (2) 최소한 응답·지도에 노출할지.**
지금은 좌표를 받아 저장만 하고 알고리즘에도 응답에도 쓰지 않아, 숙소 기능 전체가 "이름표"에 머물러 있다.

---

## 전체 우선순위 테이블

| 우선순위 | 질문 번호 | 내용 | 결정 안 하면 | 난이도 |
|---|---|---|---|---|
| 1 | D-1 / E-1 | 숙소 좌표를 알고리즘 TSP 출발점으로 반영 | "숙소 변경→재계산" 기능이 사실상 무동작. 숙소 안내가 거짓 | 백엔드 수정 (GroupInfo·AlgorithmInput·TSP 출발점) |
| 2 | A-1 / A-2 | 경고 플래그(outlier·영업시간·식사·심야·미확인) DB+DTO 노출 | 알고리즘 판정이 프론트에 0% 전달, 배지 구현 불가 | DDL + 백엔드 수정 |
| 3 | D-5 | DONE(및 TRAVELLING) 상태 편집 차단 | 종료된 여행을 누구나 편집 가능, 데이터 무결성 위험 | 백엔드 수정 (상태 가드) |
| 4 | D-3 | joined_after_voting 멤버 일정 읽기전용 강제 | 후합류 멤버가 기존 멤버 동선을 편집 | 백엔드 수정 + `canEdit` 플래그 |
| 5 | B-2 | 편집자 정보(currentlyEditingUserId) 조회 노출 | "○○님 편집 중" UX 구현 불가, 충돌 시 영문 모를 409 | 백엔드 수정 (응답 필드) |
| 6 | C-5 | Plan B 범위: 지도검색 신규 장소 교체 지원 여부 | 예비목록 밖 장소로는 교체 불가, 사용자 직접 탐색 차단 | 정책 + (확장 시)백엔드 |
| 7 | D-4 | 슬롯 삭제 / 건너뛰기(is_free_time) API | 여행 중 "오전 통째로 스킵"이 불가능 | 정책 + (필요 시)백엔드 |
| 8 | E-3 / E-4 | 숙소 위경도를 BandResponse에 노출(지도 핀) | 숙소가 이름표에 그침, 멤버는 위치 못 봄 | 백엔드 수정 (응답 필드) |
| 9 | A-3 | 빈 Day 표시 정책(응답 누락 보완) | 빈 날이 응답에서 사라져 프론트가 인지 못 함 | 정책 + (선택)백엔드 |
| 10 | B-4 / D-2 | WebSocket 이벤트 수신 시 자동 re-fetch 규약 | 알림만 오고 화면이 안 바뀜 / 본인 변경에 중복 갱신 | 프론트 정책 |
| 11 | B-1 / B-3 | 편집 락 거는 시점 + 자동저장 부재 명문화 | 동시 드래그 충돌, 미저장 변경 유실을 "저장됨"으로 오인 | 프론트 정책 (+자동저장 원하면 백엔드) |
| 12 | C-1 / C-6 | swap 확인 팝업 + 재계산 로딩 표시 | 오탭 교체로 전원 알림·재계산, 로딩 공백 | 프론트 정책 |
| 13 | C-2 | CULTURE↔NATURE 후보 "유사 카테고리" 라벨 | 다른 카테고리 추천 이유 불명 | 프론트 정책 (또는 플래그 추가) |
| 14 | A-4 / A-5 | 생성 완료 트리거 일원화 + 온보딩 1회/매번 | 중복 갱신 / 자동결과를 확정으로 오해 | 프론트 정책 |
| 15 | E-5 | Day별 다중 숙소 v1 미지원 명시 | 멀티 숙소 여행 표현 불가 | 정책 (v2 이관) |
| 16 | C-3 / C-4 | fallbackLevel 단계 표시 / altPool 재조회 UI | (데이터·API 이미 존재 — 표시만) | 프론트 정책 |

---

## 요약: 코드와 시나리오 전제가 어긋난 3가지 (최우선 확인)

1. **숙소는 저장만 되고 알고리즘·응답에 안 쓰인다** — "숙소 변경 재계산"과 "도시 중심 출발 안내"가 모두 코드와 불일치 (GroupInfo.java, ScheduleService.java:162-166, SimpleTsp.java:196).
2. **알고리즘 경고 플래그 5종이 저장 단계에서 전량 폐기된다** — 프론트가 outlier/영업시간 배지를 만들 데이터 자체가 없다 (ScheduledPlace.java:18-22 → saveSchedules ScheduleService.java:250-269).
3. **DONE 수정 차단·후합류 멤버 읽기전용이 구현되어 있지 않다** — 시나리오가 가정한 제약이 실제로는 없어, 상태와 무관하게 누구나 편집 가능 (ScheduleService.java:489/628/321).