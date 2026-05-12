# SyncTrip 알고리즘 의사코드 v2.6

**작성일:** 2026-05-12 (v2.6 패치)
**연관 문서:** `SyncTrip_인수인계문서_v6.1.md`, `SyncTrip_DDL_v6.sql`

**v2.5 → v2.6 변경 요약 (FOOD 동선 개선):**
- [FIX-47] §5.4 `insert_food_by_window` — FOOD 선택 로직을 priority 순 → 삽입 위치 기준 nearest 방식으로 교체 (동선 지그재그 방지)

**v2.4 → v2.5 변경 요약 (중간 보고서 검토 반영):**
- [FIX-42] §5.4 `insert_food_by_window` 헬퍼 명확화 (`prev_departure`, `simulate_arrivals` 정의)
- [FIX-43] RELAXED 점심 부재 의도 주석 추가
- [FIX-44] §4.3 Step 2 로드밸런싱 over_day 엣지 케이스 명시
- [FIX-45] §11.5 `restructure_day` 알고리즘 레이어 vs 편집 컨트롤러 레이어 구분
- [FIX-46] "60% 감소" 추정치 → "대폭 감소 예상" 표현 완화 (전역 3건)

**v2.3 → v2.4 변경 요약 (숙소/지도/투표 정책 통합):**
- [FIX-35] 숙소 정책 명세 — 방장만 입력, 투표 전 자유 변경, **여행 중에도 변경 가능**
- [FIX-36] Step 3 시그니처에 `current_date` 파라미터 추가 (호텔 변경 시 해당 day 이후만 재계산)
- [FIX-37] 지도 표시 정책 — Day 탭 / 카테고리 아이콘+번호 / 마커만 (경로 라인 X)
- [FIX-38] 길찾기 외부 앱 위임 — 해외=구글맵 강제 / 국내=선택 다이얼로그
- [FIX-39] A1 투표/Ready 정책 9건 명세 (취소 불가, 강제시작 경고, 멤버 추가 정책 등)
- [FIX-40] 영업시간 = 해외 전용 (국내 NULL, 카카오 API 미제공)
- [FIX-41] 알고리즘 입출력 명세 (Step 1~3, REORDER, Plan B 함수 시그니처)
- 다중 숙소 (Day별)는 v2 확장으로 명시

**v2.2 → v2.3 변경 요약 (Plan B 정식 도입):**
- [FIX-30] **Plan B 기능 정식 명세** — USR-018 + USR-031 통합
- [FIX-31] altPool 1km 2단계 폭포수 검색
- [FIX-32] Plan B 트리거 = 슬롯 Long Press → 바텀 시트
- [FIX-33] Pool Swap 정책 (방출 장소 → altPool 복귀)
- [FIX-34] 외부 API 호출은 [지도 검색] 명시적 진입 시에만
- USR-018, USR-031 통합 (단일 로직, 시점만 다름)

**v2.1 → v2.2 변경 요약 (Step 2/3 검증 + 단순화):**
- [FIX-18] Step 2 장소 < K 가드 절 (K-Means 스킵, 직접 분배)
- [FIX-19] Step 2 centroid radius 동적화 (`max_dist / 2`)
- [FIX-20] Step 2 FOOD 거리 기반 배정 (priority 순 + nearest centroid)
- [FIX-21] Step 2 로드밸런싱 임계값 `+1` 제거
- [FIX-22] Step 2 K-Means 결정론성 (centroid 동률 시 day ASC tie-break)
- [FIX-23] Step 2 수렴 조건 완화 (`< 0.05km` / 30회)
- [FIX-24] Step 2 §2-6 mainPool 보충 폐지 (빈 클러스터는 그대로)
- [FIX-25] Step 3 Nearest Neighbor 결정론성 (priority/place_id tie-break)
- [FIX-28] 영업시간 NULL 시 `OPENING_HOURS_UNVERIFIED` 경고 배지
- **Step 3 전면 단순화** — 영업시간 체크/altPool 자동 대체/자유시간 슬롯/21:00 상한 폐지
- altPool 역할 재정의 — "수동편집 시 추천 풀"로 한정

**v2.0 → v2.1 변경 (Step 1 검증):**
- [FIX-11] 싫어요 테러 장소 조기 차단 (`vote_score > 0` 컷오프)
- [FIX-12] mainPool 편입 시 `priority_score > 0` 가드 추가
- [FIX-13] 정렬 동점자(Tie-breaker) 명시
- [FIX-14] 거리 정규화 — `max_dist < 3km` 시 페널티 무시
- [FIX-15] 미투표 처리 — 투표 표명자 기준 정규화
- [FIX-16] K(여행 일수) 계산식 명시
- [FIX-17] 이상치 임계값 재정의 (절대 거리 기반)

---

## 📋 목차

1. [개요 및 철학](#1-개요-및-철학)
2. [공통 상수](#2-공통-상수)
3. [Step 1. Weighted Cost Function](#3-step-1-weighted-cost-function)
4. [Step 2. K-Means Clustering](#4-step-2-k-means-clustering)
5. [Step 3. Time Window TSP](#5-step-3-simple-order--time-tsp)
6. [REORDER 모드 (수동편집)](#6-reorder-모드-수동편집)
7. [Plan B (USR-018 + USR-031)](#7-plan-b-usr-018--usr-031)
8. [숙소 정책](#8-숙소-정책)
9. [투표/Ready 정책](#9-투표ready-정책)
10. [지도 표시 정책](#10-지도-표시-정책)
11. [알고리즘 입출력 명세](#11-알고리즘-입출력-명세)
12. [헬퍼 함수](#12-헬퍼-함수)
13. [패치 변경 이력](#13-패치-변경-이력)
14. [실구현 전 확인 필요 사항](#14-실구현-전-확인-필요-사항)
15. [논문 어필 포인트](#15-논문-어필-포인트)

---

## 1. 개요 및 철학

### 1.1 파이프라인 전체 구조

```
[투표 종료]
    ↓
Step 1. Weighted Cost Function
    → vote_score, priority_score 계산
    → mainPool / altPool 분리 (절대값 기준)
    → mainPool: 일정에 자동 배치
    → altPool:  사용자 수동편집 시 추천 풀
    ↓
Step 2. K-Means Clustering
    → 360°/K 균등 분산 centroid (radius 동적)
    → FOOD 거리 기반 배정 (priority 순)
    → Density 리밸런싱 + 로드밸런싱
    → 빈 클러스터는 그대로 (자유시간 처리 X, 사용자 수동편집)
    ↓
Step 3. Simple Order & Time TSP (v2.2 단순화)
    → 비FOOD Nearest Neighbor 정렬
    → FOOD를 Window 시간대에 위치 끼워넣기
    → 시간 할당 (이동시간 + 체류시간 누적)
    ※ 영업시간 체크 / altPool 자동 대체 / 21:00 상한 / 자유시간 슬롯 모두 폐지
    ↓
[초안 일정 확정]
    ↓
(사용자 수동편집 시)
REORDER / RESTRUCTURE / MIXED 분기
    → REORDER: 이동시간만 재계산
    → RESTRUCTURE: 해당 Day만 TSP 재실행
    → MIXED: 사용자 선택
    ↓
경고 배지 부착 (차단 X)
    → TIME_OUT_OF_MEAL_WINDOW
    → OUTSIDE_OPENING_HOURS
    → OPENING_HOURS_UNVERIFIED  [FIX-28]
    → LATE_SCHEDULE

────────────────────────────────────────
Plan B (USR-018 + USR-031, v2.3 신규)
────────────────────────────────────────
[슬롯 Long Press]
    ↓
1km 2단계 폭포수 검색 (altPool)
    1순위: 같은 카테고리 + 1km 이내
    2순위: 같은 카테고리 + 거리 무관
    ↓
바텀시트 표시 (최대 7개) + [지도 검색] 버튼
    ↓
사용자 선택 → RESTRUCTURE 트랜잭션 재활용
    + Pool Swap (방출 장소 → altPool 복귀)
    ↓
WebSocket 전원 알림

────────────────────────────────────────
호텔 변경 (v2.4 신규, FIX-35/36)
────────────────────────────────────────
[방장이 [숙소 변경] 클릭, TRAVELLING 상태]
    ↓
PUT /api/groups/{id}/accommodation
    ↓
Step 3 호출 (current_date = TODAY, existing_schedules 전달)
    → 과거 day: 그대로 보존
    → 현재/미래 day: 새 출발점으로 재계산
    ↓
DB UPDATE (변경된 day만)
    ↓
WebSocket 전원 알림 + 안내
```

### 1.2 핵심 설계 철학

> **"알고리즘은 합리적 초안을 만들고, 사용자가 자유롭게 조정한다."**
> **분 단위 정확성보다 동선 가이드로서의 합리성을 추구한다.**

- 완벽한 최종 일정을 목표로 하지 않음 — **빠르고 간단한 초안**
- 사용자는 알고리즘이 제시한 시간표를 분 단위로 따르지 않음
- Step 1, 2가 합리적 후보를 선별했으므로 Step 3는 **단순 정렬 + 시간 할당**만 수행
- 영업시간, 21:00 상한 등 미시 제약은 **알고리즘이 강제하지 않고** UI 경고 + 수동편집으로 위임
- K-Means는 최초 1회만 실행, 이후 수정은 TSP로만 처리

---

## 2. 공통 상수

```
// 이동 속도 [FIX-9]
SPEED_KMH        = 25       // 도심 평균 이동 속도 (km/h, 도보+대중교통)

// 하루 시간 경계
DAY_START_TIME   = 09:00    // 하루 시작 (조식 후 활동 시작 가정)
LATE_WARN_TIME   = 22:00    // 22:00 이후 시작 일정에 LATE_SCHEDULE 경고 배지
// ※ DAY_END_TIME(21:00 상한)은 v2.2에서 폐지 — Density 한도가 일정량 제어

// Density 한도
RELAXED_DENSITY  = 5
PACKED_DENSITY   = 8

// FOOD 하루 쿼터
RELAXED_FOOD_PER_DAY = 1
PACKED_FOOD_PER_DAY  = 2

// FOOD Time Window
// [FIX-43] RELAXED는 저녁 한 끼만 명시적 슬롯으로 잡음 — 의도된 설계
//   - 점심은 사용자 자율 처리 (편의점/포장/관광 중 해결)
//   - 점심도 명시적 슬롯으로 원하면 PACKED 선택
//   - "여유로운 여행 = 식사보다 관광 위주" 컨셉
RELAXED_FOOD_WINDOWS = [(17:00, 20:00)]              // 저녁만
PACKED_FOOD_WINDOWS  = [(11:00, 14:00), (17:00, 20:00)]  // 점심+저녁

// 지리 계산
EARTH_RADIUS_KM  = 6371
KM_PER_LAT_DEG   = 111      // 근사값

// 거리 페널티 [FIX-14]
DIST_PENALTY_FLOOR_KM = 3.0   // 모든 장소가 3km 내면 페널티 무시

// 이상치 임계값 [FIX-17] — 절대 거리 기반
OUTLIER_DIST_KM = 30.0        // 도시 외곽/원거리 기준 (30km 초과)

// K-Means 수렴 [FIX-23]
KMEANS_CONVERGE_KM   = 0.05   // centroid 변화 50m 이하면 수렴 간주
KMEANS_MAX_ITER      = 30     // 수렴 최대 반복 횟수

// Plan B [FIX-31]
PLAN_B_NEAR_RADIUS_KM = 1.0   // 1순위: 같은 카테고리 + 1km 이내
PLAN_B_MAX_RESULTS    = 7     // 추천 후보 최대 개수
```

### 카테고리별 Density Point

| 카테고리 | Density | 기본 체류시간 |
|---|---|---|
| ACTIVITY | 3 | 120분 |
| CULTURE | 2 | 90분 |
| NATURE | 2 | 90분 |
| SHOPPING | 1 | 60분 |
| ETC | 1 | 60분 |
| **FOOD** | **0** (쿼터 별도) | 60분 |

---

## 3. Step 1. Weighted Cost Function

### 3.1 목적
- 투표 결과를 인원 정규화된 점수로 변환
- 거리 페널티를 적용한 `priority_score` 산출
- 통과/altPool/탈락 3단계 분류
- FOOD 쿼터 우선 확보 후 비FOOD를 Density 한도 내 편입

### 3.1.1 mainPool / altPool 역할 (v2.2 재정의)

| Pool | 역할 |
|---|---|
| **mainPool** | Step 2(K-Means) → Step 3(TSP) 거쳐 자동 일정에 배치 |
| **altPool** | 사용자가 수동편집 시 추천할 후보 풀 (USR-018, USR-031). 알고리즘이 자동 대체에 사용하지 않음 |

> v2.1까지는 altPool이 Step 3 영업시간 위반 시 자동 대체용으로 쓰였으나,
> v2.2 단순화로 자동 대체 로직을 폐지함. altPool은 순수히 "사용자 자율 조정용 풀".

### 3.2 의사코드

```
FUNCTION step1_weighted_cost(places, members, group, travel_style):

  // ─────────────────────────────────────
  // 0. 사전 계산
  // [FIX-16] K = 여행 일수 (start_date ~ end_date 포함)
  // ─────────────────────────────────────
  total_members = members.count
  destination   = {lat: group.destination_lat, lng: group.destination_lng}
  K = (group.end_date - group.start_date).days + 1
  
  // ─────────────────────────────────────
  // 1. 투표 결과 집계
  // [FIX-1] bonus_rate 제거: 본인 자동 LIKE(result=0)가
  //         like_count에 이미 포함되므로 중복 북마크는 자연 반영
  // [FIX-15] 미투표 처리: 투표 표명자(LIKE+DISLIKE) 기준 정규화
  //          - 시간 초과/강제 시작으로 일부 멤버가 미투표한 경우
  //            그들을 페널티로 취급하지 않음
  //          - 통과 기준(passed_threshold)은 여전히 절대 LIKE 수 기준 유지
  // ─────────────────────────────────────
  FOR each place IN places:
    place.like_count    = votes.count(place, result IN [1, 0])
    place.dislike_count = votes.count(place, result = -1)
    
    // 적극 표명한 사람만 분모로 (미투표는 row 자체가 없음)
    total_voters = place.like_count + place.dislike_count
    
    IF total_voters == 0:
      // 누구도 투표하지 않은 장소 (강제 시작 등)
      // → passed_threshold(>=1)에서 자동 탈락하므로 안전
      place.like_rate    = 0
      place.dislike_rate = 0
      place.vote_score   = 0
    ELSE:
      place.like_rate    = place.like_count / total_voters
      place.dislike_rate = place.dislike_count / total_voters
      place.vote_score   = (place.like_rate × 2) − place.dislike_rate
  
  // ─────────────────────────────────────
  // 2. Haversine 거리 계산 + 정규화
  // [FIX-10a] max_dist = 0 예외 처리
  // [FIX-14] max_dist < DIST_PENALTY_FLOOR_KM(3km)이면 페널티 무시
  //          - 모든 장소가 도보권에 있는 경우 거리는 의사결정 요소 아님
  //          - "우물 안 개구리" 왜곡 방지
  // ─────────────────────────────────────
  FOR each place IN places:
    place.dist = haversine(destination, place)
  
  max_dist = MAX(place.dist for place IN places)
  
  IF max_dist == 0 OR max_dist < DIST_PENALTY_FLOOR_KM:
    // 모든 장소가 도보권 → 거리 페널티 적용 안 함
    FOR each place IN places:
      place.norm_dist = 0
  ELSE:
    FOR each place IN places:
      place.norm_dist = place.dist / max_dist
  
  FOR each place IN places:
    place.priority_score = (place.vote_score × 0.7)
                         − (place.norm_dist × 0.3)
  
  // ─────────────────────────────────────
  // 3. 투표 통과 필터
  // [FIX-2] 절대값 기준 전환 (비율 기반 폐기)
  // [FIX-11] 싫어요 테러 장소 조기 차단 (vote_score > 0 컷오프)
  //          - LIKE 수가 alt 범위에 들어와도, DISLIKE가 압도적이면 탈락
  //          - "갈등 해소" 기획 철학 + Step 3 자동 대체 오염 방지
  // ─────────────────────────────────────
  passed_threshold = CEIL(total_members × 0.5)
  alt_min          = MAX(1, passed_threshold − 2)
  alt_max          = passed_threshold − 1
  
  passed     = places.filter(like_count >= passed_threshold)
  alt_rescue = places.filter(
                 like_count IN [alt_min, alt_max]
                 AND vote_score > 0
               )
  // 그 외는 완전 탈락 (altPool 진입 불가)
  
  altPool = alt_rescue.copy()
  
  // ─────────────────────────────────────
  // 4. FOOD 쿼터 먼저 확보
  // [FIX-13] 정렬 동점자(Tie-breaker) 명시
  //          1순위: priority_score DESC
  //          2순위: like_count DESC
  //          3순위: dist ASC (가까운 곳 우선)
  //          4순위: place_id ASC (결정론적 순서 보장)
  // ─────────────────────────────────────
  food_places = passed.filter(category = FOOD)
                      .sort(priority_score DESC,
                            like_count DESC,
                            dist ASC,
                            place_id ASC)
  
  IF travel_style = PACKED:
    food_quota = K × PACKED_FOOD_PER_DAY
  ELSE:
    food_quota = K × RELAXED_FOOD_PER_DAY
  
  food_main = food_places[0 : food_quota]
  food_alt  = food_places[food_quota :]
  altPool.addAll(food_alt)
  
  // ─────────────────────────────────────
  // 5. 비FOOD를 Density Point 한도 내 편입
  // [FIX-12] priority_score > 0 가드 추가
  //          - 통과선은 넘었지만 종합 점수가 음수면 mainPool 진입 차단
  //          - 음수 priority 장소는 altPool로 강등 (단, FIX-11 조건 충족 시)
  // [FIX-13] 정렬 동점자(Tie-breaker) 명시
  // ─────────────────────────────────────
  IF travel_style = PACKED:
    density_limit = PACKED_DENSITY
  ELSE:
    density_limit = RELAXED_DENSITY
  
  total_density_budget = density_limit × K
  
  non_food = passed.filter(category != FOOD)
                   .sort(priority_score DESC,
                         like_count DESC,
                         dist ASC,
                         place_id ASC)
  
  mainPool    = food_main.copy()
  accumulated = 0
  
  FOR each place IN non_food:
    // [FIX-12] priority_score 음수 방어 — mainPool 차단
    IF place.priority_score <= 0:
      // 통과선은 넘었지만 종합 점수가 0 이하 → altPool 강등
      // (vote_score > 0 조건 충족하므로 altPool 진입 가능)
      altPool.add(place)
      CONTINUE
    
    IF accumulated + place.density_point <= total_density_budget:
      mainPool.add(place)
      accumulated += place.density_point
    ELSE:
      altPool.add(place)
  
  // ─────────────────────────────────────
  // 6. 이상치 후보 마킹 (Step 2-8에서 경고 발행)
  // [FIX-17] 절대 거리 기반 (FIX-14로 norm_dist 의미 변동 → 직접 km 사용)
  //          OUTLIER_DIST_KM(30km) 초과 = 도시 외곽/원거리 후보
  // ─────────────────────────────────────
  FOR each place IN mainPool:
    IF place.dist > OUTLIER_DIST_KM:
      place.is_outlier_candidate = TRUE
  
  // altPool 최종 정렬 [FIX-13]
  altPool = altPool.sort(priority_score DESC,
                         like_count DESC,
                         dist ASC,
                         place_id ASC)
  FOR i, place IN enumerate(altPool):
    place.alt_rank = i + 1
  
  RETURN mainPool, altPool, density_limit
```

### 3.3 altPool 기준 동작 검증

**기본 통과 기준 (절대 LIKE 수)**

| 멤버 수 | 통과선 | altPool LIKE 범위 | 탈락선 |
|---|---|---|---|
| 3명 | 2표 | 1표 | 0표 |
| 4명 | 2표 | 1표 (0은 제외) | 0표 |
| 5명 | 3표 | 1~2표 | 0표 |
| 6명 | 3표 | 1~2표 | 0표 |
| 7명 | 4표 | 2~3표 | 0~1표 |
| 8명 | 4표 | 2~3표 | 0~1표 |

**[FIX-11] vote_score 컷오프 적용 예시 (6명 그룹)**

| LIKE | DISLIKE | 미투표 | vote_score | LIKE 기준 | vote_score 기준 | 최종 |
|---|---|---|---|---|---|---|
| 4 | 0 | 2 | 2.0 | passed | ✅ | mainPool |
| 3 | 0 | 3 | 2.0 | passed | ✅ | mainPool |
| 2 | 0 | 4 | 2.0 | alt | ✅ | altPool |
| 2 | 1 | 3 | 1.0 | alt | ✅ | altPool |
| **2** | **4** | **0** | **-0.67** | **alt** | **❌** | **탈락** [FIX-11] |
| 1 | 5 | 0 | -0.5 | alt | ❌ | 탈락 |

---

## 4. Step 2. K-Means Clustering

### 4.1 목적
- mainPool의 비FOOD 장소를 K일(클러스터)로 분산
- FOOD는 priority 순으로 가장 가까운 centroid의 day에 배정 (FIX-20)
- Density와 장소 개수 양쪽에서 균형
- 빈 클러스터(장소 < K)는 K-Means 스킵하고 직접 분배 (FIX-18)

### 4.2 의사코드

```
FUNCTION step2_kmeans(mainPool, altPool, K, destination, density_limit, max_dist):

  // ─────────────────────────────────────
  // 2-1. FOOD 분리
  // ─────────────────────────────────────
  food_places     = mainPool.filter(category = FOOD)
  non_food_places = mainPool.filter(category != FOOD)
  
  // ─────────────────────────────────────
  // 2-1.5. 가드 절 — 장소 < K [FIX-18]
  // K-Means 돌릴 장소가 K개 미만이면 클러스터링 스킵
  // 장소를 1개씩 분배 + FOOD 거리 기반 배정 → Step 3 위임
  // ─────────────────────────────────────
  IF non_food_places.count < K:
    clusters = {1:[], 2:[], ..., K:[]}
    FOR i, place IN enumerate(non_food_places):
      day = (i MOD K) + 1
      clusters[day].add(place)
    
    // centroid는 각 클러스터의 단일 장소 또는 destination으로 폴백
    centroids = {}
    FOR each day IN 1 to K:
      IF clusters[day].isNotEmpty:
        p = clusters[day][0]
        centroids[day] = {lat: p.lat, lng: p.lng, day: day}
      ELSE:
        centroids[day] = {lat: destination.lat, lng: destination.lng, day: day}
    
    // FOOD 거리 기반 배정 (§2-4와 동일 로직)
    GOTO step_2_4
  
  // ─────────────────────────────────────
  // 2-2. 초기 centroid 설정 (360°/K 균등 분산)
  // [FIX-4]  라디안 변환 + 경도 위도 보정 (cos(lat))
  // [FIX-19] radius 동적화 — max_dist / 2 (최소 1km 보장)
  // ─────────────────────────────────────
  centroids = {}
  radius_km = MAX(max_dist / 2, 1.0)
  dest_lat_rad = destination.lat × PI / 180
  
  FOR i IN 0 to K-1:
    angle_rad = (2 × PI / K) × i
    dlat_deg  = (radius_km / KM_PER_LAT_DEG) × cos(angle_rad)
    dlng_deg  = (radius_km / (KM_PER_LAT_DEG × cos(dest_lat_rad)))
              × sin(angle_rad)
    
    centroids[i+1] = {
      lat: destination.lat + dlat_deg,
      lng: destination.lng + dlng_deg,
      day: i + 1
    }
  
  // ─────────────────────────────────────
  // 2-3. K-Means 수렴
  // [FIX-10b] 빈 클러스터 재배치 처리
  // [FIX-22]  centroid 동률 시 day ASC tie-break (결정론성)
  // [FIX-23]  수렴 조건 완화 (KMEANS_CONVERGE_KM, KMEANS_MAX_ITER)
  // ─────────────────────────────────────
  iteration = 0
  clusters = {}
  
  WHILE TRUE:
    // 장소 → 가장 가까운 centroid 배정 (동률 시 day ASC)
    clusters = {1:[], 2:[], ..., K:[]}
    FOR each place IN non_food_places:
      nearest_day = day with MIN haversine(place, centroids[day])
                    tie-break by day ASC
      clusters[nearest_day].add(place)
    
    // 빈 클러스터 처리
    FOR each day IN 1 to K:
      IF clusters[day].isEmpty:
        donor_day = day with MAX clusters[day].count
        farthest = place IN clusters[donor_day] with
                   MAX haversine(place, centroids[donor_day])
                   tie-break by place_id ASC
        clusters[donor_day].remove(farthest)
        clusters[day].add(farthest)
    
    // centroid 재계산
    new_centroids = {}
    FOR each day IN 1 to K:
      new_lat = AVG(p.lat for p IN clusters[day])
      new_lng = AVG(p.lng for p IN clusters[day])
      new_centroids[day] = {lat: new_lat, lng: new_lng, day: day}
    
    // 수렴 체크
    max_change = MAX(haversine(centroids[d], new_centroids[d])
                     for d IN 1 to K)
    
    iteration += 1
    centroids = new_centroids
    
    IF max_change < KMEANS_CONVERGE_KM OR iteration >= KMEANS_MAX_ITER:
      BREAK
  
  step_2_4:  // 가드 절(2-1.5)에서 점프하는 라벨
  
  // ─────────────────────────────────────
  // 2-4. FOOD 거리 기반 배정 (priority 순) [FIX-20]
  //   - priority 높은 FOOD부터 가장 가까운 centroid의 day에 배정
  //   - day의 FOOD quota 가득 차면 다음 사용 가능한 day 중 가까운 곳 선택
  //   - 모든 day가 quota 가득 차면 altPool로 (이론상 quota 계산상 발생 안 함)
  // ─────────────────────────────────────
  IF travel_style = PACKED:
    food_per_day_quota = PACKED_FOOD_PER_DAY
  ELSE:
    food_per_day_quota = RELAXED_FOOD_PER_DAY
  
  food_sorted = food_places.sort(priority_score DESC,
                                  like_count DESC,
                                  place_id ASC)
  
  FOR each food IN food_sorted:
    available_days = days where 
                     clusters[day].count(category = FOOD) < food_per_day_quota
    
    IF available_days.isEmpty:
      altPool.add(food)
      CONTINUE
    
    nearest_day = day IN available_days with 
                  MIN haversine(food, centroids[day])
                  tie-break by day ASC
    clusters[nearest_day].add(food)
  
  // ─────────────────────────────────────
  // 2-5. Density 리밸런싱
  // ─────────────────────────────────────
  max_rebalance_iter = K × 2
  iter = 0
  
  WHILE iter < max_rebalance_iter:
    over_days = []
    FOR each day IN 1 to K:
      density_sum = SUM(p.density_point
                        for p IN clusters[day]
                        if p.category != FOOD)
      IF density_sum > density_limit:
        over_days.add(day)
    
    IF over_days.isEmpty:
      BREAK
    
    FOR each day IN over_days:
      candidates = clusters[day]
                   .filter(category != FOOD)
                   .sort(priority_score ASC)
      
      FOR each place IN candidates:
        allow_dist = max_dist × 0.3
        moved = FALSE
        
        FOR each other_day IN 1 to K:
          IF other_day == day: CONTINUE
          
          other_density = SUM(p.density_point
                              for p IN clusters[other_day]
                              if p.category != FOOD)
          
          place_to_other = haversine(place, centroids[other_day])
          
          IF place_to_other <= allow_dist
          AND other_density + place.density_point <= density_limit:
            clusters[day].remove(place)
            clusters[other_day].add(place)
            moved = TRUE
            BREAK
        
        IF NOT moved:
          // 이동 불가 → altPool로 강등
          clusters[day].remove(place)
          altPool.add(place)
        
        // 현재 Day 재체크
        curr_density = SUM(p.density_point
                           for p IN clusters[day]
                           if p.category != FOOD)
        IF curr_density <= density_limit:
          BREAK
    
    iter += 1
  
  // ─────────────────────────────────────
  // 2-6. (폐지) [FIX-24]
  //   v2.1까지의 mainPool 보충 로직 폐지.
  //   빈 클러스터는 그대로 두며, 사용자 수동편집(USR-018)으로 보충.
  //   "빠르고 간단한 초안" 철학에 부합.
  // ─────────────────────────────────────
  
  // ─────────────────────────────────────
  // 2-7. 로드밸런싱
  // [FIX-5]  density 제약 재검증
  // [FIX-10c] avg FLOOR·CEIL 경계 명시
  // [FIX-21]  임계값 +1 제거 (avg_ceil 자체를 상한으로)
  // [FIX-44] 엣지 케이스 — over_day지만 이동 대상 없음
  //   상황: count > avg_ceil이지만 candidates(비FOOD)를 다른 day로 이동 불가
  //         (거리 제약 또는 density 한도 위반)
  //   처리: candidates 다 돌고 못 옮기면 그대로 유지 (강제 분리 X)
  //   근거: "사용자 자율 조정" 철학 — 알고리즘이 강제하지 않고 수동편집 위임
  //   주의: FOOD가 많이 몰린 day가 count 기준 over_day가 되어도
  //         FOOD는 이동 대상에서 제외되므로 candidates가 적을 수 있음
  // ─────────────────────────────────────
  total_places = SUM(clusters[day].count for day IN 1 to K)
  avg_floor    = FLOOR(total_places / K)
  avg_ceil     = CEIL(total_places / K)
  allow_dist   = max_dist × 0.3
  
  FOR each over_day IN clusters where count > avg_ceil:
    candidates = clusters[over_day]
                 .filter(category != FOOD)
                 .sort(priority_score ASC)
    
    FOR each place IN candidates:
      FOR each under_day IN clusters where count < avg_floor:
        under_density = SUM(p.density_point
                            for p IN clusters[under_day]
                            if p.category != FOOD)
        place_to_under = haversine(place, centroids[under_day])
        
        // density도 함께 체크
        IF place_to_under <= allow_dist
        AND under_density + place.density_point <= density_limit:
          clusters[over_day].remove(place)
          clusters[under_day].add(place)
          BREAK
      
      IF clusters[over_day].count <= avg_ceil:
        BREAK
  
  // ─────────────────────────────────────
  // 2-8. 이상치 경고 플래그
  // ─────────────────────────────────────
  FOR each day IN 1 to K:
    FOR each place IN clusters[day]:
      IF place.is_outlier_candidate
      AND clusters[day].count < 2:
        place.warnings = place.warnings OR []
        place.warnings.add("OUTLIER_FULL_DAY")
  
  RETURN clusters, centroids, altPool
```

### 4.3 v2.2 패치 영향 정리

| FIX ID | 위치 | 효과 |
|---|---|---|
| FIX-18 | §2-1.5 가드 절 | `non_food < K` 시 K-Means 스킵, 직접 분배 |
| FIX-19 | §2-2 centroid radius | `max_dist / 2`로 동적화 (수렴 가속) |
| FIX-20 | §2-4 FOOD 배정 | 거리 기반 (가까운 centroid 우선) |
| FIX-21 | §2-7 로드밸런싱 | `count > avg_ceil` (기존 +1 제거) |
| FIX-22 | §2-3 클러스터 배정 | 동률 시 `day ASC` tie-break |
| FIX-23 | §2-3 수렴 조건 | 50m / 30회로 완화 |
| FIX-24 | §2-6 폐지 | 빈 클러스터 = 사용자 수동편집으로 보충 |

---

## 5. Step 3. Simple Order & Time TSP

> **v2.2 전면 단순화.**
> 이전 버전(Time Window TSP)은 영업시간 체크, altPool 자동 대체, 자유시간 슬롯 자동 추가, 21:00 상한 등을 알고리즘이 강제했음.
> v2.2부터는 "분 단위 정확성보다 동선 가이드" 철학에 따라 **순수 정렬 + 시간 할당**만 수행.

### 5.1 목적
- 각 Day 클러스터의 비FOOD 장소를 Nearest Neighbor로 방문 순서 결정
- FOOD를 적절한 시간대(Window)에 위치 끼워넣기
- 출발점부터 누적 시간 할당 (이동시간 + 체류시간)

### 5.2 폐지된 기능 (v2.1 대비)

| 기능 | 폐지 이유 | 대체 방식 |
|---|---|---|
| 영업시간 `is_open_during` 체크 | opening_hours 데이터 불완전 + 사용자가 알아서 조정 | UI 경고 배지 (FIX-28) |
| altPool 자동 대체 | "분 단위 최적화 강박" — 사용자 영향 없음 | 사용자 수동편집 (USR-018) |
| 자유시간 슬롯 자동 추가 | 빈 시간은 빈 시간으로 OK | UI에서 자동 표시 |
| 21:00 하루 상한 | Step 1 Density 한도가 일정량 제어 | 22:00 이후 시작은 LATE_SCHEDULE 배지 |
| FOOD Window "창 지남" 처리 | 단순화로 자연 해결 | 위치 끼워넣기로 사전 방지 |

### 5.3 의사코드

```
FUNCTION step3_simple_tsp(
    clusters, K, accommodation, destination, travel_style,
    start_date,                  // [FIX-36] 그룹 시작일
    current_date = null,         // [FIX-36] 호텔 변경 시 사용 (null이면 신규 생성)
    existing_schedules = null    // [FIX-36] 호텔 변경 시 과거 day 보존용
):
  
  // 출발점 결정
  IF accommodation.lat is NOT NULL:
    startPoint = accommodation
  ELSE:
    startPoint = destination
  
  // FOOD Time Window (위치 끼워넣기 기준)
  IF travel_style = PACKED:
    food_windows = PACKED_FOOD_WINDOWS    // [(11:00,14:00),(17:00,20:00)]
  ELSE:
    food_windows = RELAXED_FOOD_WINDOWS   // [(17:00,20:00)]
  
  schedules = []
  
  FOR each day IN 1 to K:
    day_date = start_date + (day - 1)
    
    // [FIX-36] 호텔 변경 시 과거 day는 재계산하지 않고 기존 보존
    IF current_date is NOT NULL AND day_date < current_date:
      schedules.add(existing_schedules.find(day_number = day))
      CONTINUE
    
    places      = clusters[day]
    food_places = places.filter(category = FOOD)
                        .sort(priority_score DESC,
                              like_count DESC,
                              place_id ASC)
    non_food    = places.filter(category != FOOD)
    
    // ─────────────────────────────────────
    // 1. 비FOOD 방문 순서 결정 (Nearest Neighbor)
    // [FIX-25] 결정론성 — 동률 시 priority DESC, place_id ASC tie-break
    // ─────────────────────────────────────
    ordered_non_food = []
    current_pos = startPoint
    remaining   = non_food.copy()
    
    WHILE remaining.isNotEmpty:
      nearest = place IN remaining with 
                MIN haversine(current_pos, place)
                tie-break by priority_score DESC, place_id ASC
      ordered_non_food.add(nearest)
      remaining.remove(nearest)
      current_pos = nearest
    
    // ─────────────────────────────────────
    // 2. FOOD를 Window 시간대에 위치 끼워넣기
    //    - 시뮬레이션으로 비FOOD만 시간 할당 가정 후
    //    - 각 Window 시작 시각 도달 직전 위치에 FOOD 삽입
    // ─────────────────────────────────────
    final_order = insert_food_by_window(
      ordered_non_food, food_places, food_windows, startPoint
    )
    
    // ─────────────────────────────────────
    // 3. 시간 할당 (단순 누적)
    // ─────────────────────────────────────
    current_time = DAY_START_TIME
    current_pos  = startPoint
    day_schedule = []
    slot_order   = 1
    
    FOR each place IN final_order:
      travel    = haversine(current_pos, place) / SPEED_KMH × 60   // 분
      arrival   = current_time + travel
      duration  = place.estimated_duration
      departure = arrival + duration
      
      day_schedule.add({
        slot_order:   slot_order,
        place:        place,
        start_time:   arrival,
        duration:     duration,
        travel_time:  travel,
        is_free_time: FALSE,
        warnings:     compute_warnings(place, arrival, food_windows)  // [FIX-28]
      })
      
      current_time = departure
      current_pos  = place
      slot_order  += 1
    
    schedules.add(day_schedule)
  
  RETURN schedules, altPool   // altPool은 변경 없이 그대로 반환
```

### 5.4 헬퍼 — `insert_food_by_window` [FIX-42]

> **v2.5 명확화:** `prev_departure`, `simulate_arrivals` 정의 추가.
> 백엔드 구현 시 모호함 제거.

```
FUNCTION simulate_arrivals(non_food_order, startPoint):
  // 비FOOD만 있다고 가정하고 각 슬롯의 도착 시각 시뮬레이션
  arrivals   = []   // arrivals[i] = i번째 슬롯 도착 시각
  departures = []   // departures[i] = i번째 슬롯 출발 시각 (= arrival + duration)
  
  current_time = DAY_START_TIME
  current_pos  = startPoint
  
  FOR i, place IN enumerate(non_food_order):
    travel    = haversine(current_pos, place) / SPEED_KMH × 60
    arrival   = current_time + travel
    departure = arrival + place.estimated_duration
    
    arrivals.add(arrival)
    departures.add(departure)
    
    current_time = departure
    current_pos  = place
  
  RETURN arrivals, departures


FUNCTION insert_food_by_window(non_food_order, food_places, windows, startPoint):
  // 비FOOD만 시뮬레이션 → 도착/출발 시각 배열 획득
  arrivals, departures = simulate_arrivals(non_food_order, startPoint)
  
  // [FIX-47] food_places 전체에 consumed 플래그 초기화
  FOR each food IN food_places:
    food.consumed = FALSE
  
  result = []
  
  FOR i, place IN enumerate(non_food_order):
    // 현재 슬롯 직전에 FOOD 끼워넣을 수 있는지 검사
    FOR each window IN windows:
      IF window.consumed: CONTINUE
      IF food_places.filter(NOT consumed).isEmpty: BREAK
      
      // prev_departure = i번째 슬롯 직전의 출발 시각
      //   - i == 0: 출발점에서 09:00 출발 (DAY_START_TIME)
      //   - i >= 1: i-1번째 슬롯 종료 후 출발 시각 (departures[i-1])
      IF i == 0:
        prev_departure = DAY_START_TIME
      ELSE:
        prev_departure = departures[i-1]
      
      // 끼워넣기 조건:
      //   1. i번째 슬롯 도착 시각이 window 시작 이후 (window 시작 전 도착하면 의미 X)
      //   2. 직전 슬롯 출발 시각이 window 종료 전 (window 끝나면 끼워넣을 수 없음)
      //   → 두 조건 만족 시 i번째 슬롯 직전에 FOOD 삽입 가능
      IF arrivals[i] >= window.start AND prev_departure <= window.end:
        // [FIX-47] priority 순 고정 선택 → 삽입 직전 위치 기준 nearest 선택으로 교체
        // 삽입 직전 위치: i==0이면 startPoint, 아니면 직전 비FOOD 장소
        insert_pos = startPoint IF i == 0 ELSE non_food_order[i-1]
        
        // 미사용 FOOD 중 삽입 위치와 가장 가까운 것 선택
        // (Step 2에서 이미 Day 권역 기준으로 배정됐으므로 후보 수 소규모)
        unused_food = food_places.filter(NOT consumed)
        nearest_food = place IN unused_food with
                       MIN haversine(insert_pos, place)
                       tie-break by priority_score DESC, place_id ASC
        nearest_food.consumed = TRUE
        
        result.add(nearest_food)
        window.consumed = TRUE
        BREAK
    
    result.add(place)
  
  // 남은 FOOD는 일정 끝에 추가 (저녁 등 마지막 Window)
  // [FIX-47] food_idx 순회 → consumed 플래그 기반 순회로 교체
  FOR each food IN food_places.filter(NOT consumed):
    result.add(food)
  
  RETURN result
```

**핵심 의도:**
- 비FOOD만 가정한 일정 시뮬레이션으로 시간 배열 획득
- 각 비FOOD 슬롯 직전에 FOOD를 끼워넣을 수 있는지 검사
- Window 시간대(점심/저녁)에 자연스럽게 배치
- 못 끼워넣은 FOOD는 일정 끝에 추가

> 구현 디테일은 백엔드 자율. 핵심 의도와 위 조건만 정확히 구현하면 OK.

### 5.5 헬퍼 — `compute_warnings` [FIX-28]

```
FUNCTION compute_warnings(place, arrival_time, food_windows):
  warnings = []
  
  // FOOD가 식사 Window 밖
  IF place.category = FOOD:
    in_window = ANY w IN food_windows : arrival_time IN [w.start, w.end]
    IF NOT in_window:
      warnings.add("TIME_OUT_OF_MEAL_WINDOW")
  
  // 영업시간 데이터 미확인 [FIX-28]
  IF place.opening_hours IS NULL OR place.opening_hours.isEmpty:
    warnings.add("OPENING_HOURS_UNVERIFIED")
  ELSE:
    // 데이터 있는 경우만 영업시간 검증
    departure = arrival_time + place.estimated_duration
    IF NOT place.is_open_during(arrival_time, departure):
      warnings.add("OUTSIDE_OPENING_HOURS")
  
  // 늦은 시작
  IF arrival_time >= LATE_WARN_TIME:
    warnings.add("LATE_SCHEDULE")
  
  RETURN warnings
```

### 5.6 v2.2 단순화 효과

- **알고리즘 코드 대폭 감소 예상** (영업시간/altPool/자유시간/21:00 처리 폐지로 코드 라인 수 큰 폭 축소)
- **결정론성 보장** — Tie-breaker로 같은 입력에 같은 결과
- **응답 속도 향상** — 영업시간/altPool 검색 루프 제거
- **사용자 자율성 극대화** — UI 경고만 표시, 모든 조정은 수동편집

---

## 6. REORDER 모드 (수동편집)

### 6.1 목적
- 사용자 Drag & Drop 시 편집 유형별 차등 재계산
- 제약 위반 시 차단이 아닌 **경고 배지**만 부착
- WebSocket으로 그룹 전체 실시간 동기화

### 6.2 편집 유형 분기

```
REORDER     : 순서만 변경 (장소 집합 동일) → 이동시간만 재계산 (1-pass)
RESTRUCTURE : 장소 집합 변경 (추가/삭제/교체) → 해당 Day만 TSP 재실행
MIXED       : 혼합 → 사용자 선택
              [A] 내 순서 그대로 (REORDER 처리)
              [B] 최적 순서 재계산 (RESTRUCTURE 처리)
```

### 6.3 의사코드 — 편집 세션 컨트롤러

```
FUNCTION handle_edit_session(group, day, editor):
  
  // ─── 그룹 락 ───
  group.lock(editor)
  BROADCAST "편집 중이에요 ✏️" TO all_members
  
  // ─── 편집 완료 시 ───
  ON editor.complete(new_order, edit_type):
    
    IF edit_type == 'REORDER':
      // 순서만 바뀜 — TSP 재실행 X, 이동시간만 재계산
      recompute_reorder(group, day, new_order)
    
    ELSE IF edit_type == 'RESTRUCTURE':
      // 장소 집합이 바뀜 — 해당 Day만 TSP 재실행
      new_schedule = step3_tsp(
        clusters      = {day: new_order.places},
        K             = 1,
        accommodation = group.accommodation,
        destination   = group.destination,
        travel_style  = group.travel_style,
        altPool       = group.altPool
      )
      schedules.update(day, new_schedule)
    
    ELSE IF edit_type == 'MIXED':
      // 장소와 순서 모두 변경 — 사용자 선택
      user_choice = ASK_USER(
        "순서와 장소를 모두 바꾸셨어요. 어떻게 할까요?",
        ["내 순서 그대로", "최적 순서로 다시 계산"]
      )
      IF user_choice == "내 순서 그대로":
        recompute_reorder(group, day, new_order)
      ELSE:
        new_schedule = step3_tsp(...)
        schedules.update(day, new_schedule)
    
    group.unlock()
    BROADCAST schedules TO all_members
  
  // ─── 5분 타임아웃 ───
  ON timeout(300s):
    schedules.save()
    group.unlock()
    NOTIFY editor "자동 저장됐어요"
    BROADCAST schedules TO all_members
```

### 6.4 의사코드 — REORDER 재계산 (경고 배지)

```
FUNCTION recompute_reorder(group, day, new_order):
  
  // [FIX-3] 단순 경고 모드 — 차단 없음
  // 사용자가 Drag로 결정한 순서를 100% 존중
  
  // 출발점 결정
  IF group.accommodation.lat is NOT NULL:
    startPoint = group.accommodation
  ELSE:
    startPoint = group.destination
  
  food_windows = (group.travel_style = PACKED)
                 ? PACKED_FOOD_WINDOWS
                 : RELAXED_FOOD_WINDOWS
  
  current_time = DAY_START_TIME
  current_pos  = startPoint
  
  FOR each place IN new_order:  // 사용자 지정 순서 그대로
    travel = haversine(current_pos, place) / SPEED_KMH × 60
    place.start_time  = current_time + travel
    place.travel_time = travel
    place.duration    = place.estimated_duration
    
    // ─── 경고 수집 (차단 X) ───
    place.warnings = []
    
    // FOOD가 식사 Window 밖
    IF place.category == FOOD:
      in_window = FALSE
      FOR each w IN food_windows:
        IF place.start_time IN [w.start, w.end]:
          in_window = TRUE
          BREAK
      IF NOT in_window:
        place.warnings.add("TIME_OUT_OF_MEAL_WINDOW")
    
    // 영업시간 외 [FIX-7]
    departure = place.start_time + place.duration
    IF NOT place.is_open_during(place.start_time, departure):
      place.warnings.add("OUTSIDE_OPENING_HOURS")
    
    // 하루 늦은 일정
    IF place.start_time >= LATE_WARN_TIME:
      place.warnings.add("LATE_SCHEDULE")
    
    current_time = place.start_time + place.duration
    current_pos  = place
  
  schedules.update(day, new_order)
```

### 6.5 경고 배지 종류

| 코드 | 표시 | 의미 |
|---|---|---|
| `TIME_OUT_OF_MEAL_WINDOW` | 🕐 회색 | FOOD가 식사 Window 밖 |
| `OUTSIDE_OPENING_HOURS` | ⚠️ 노랑 | 영업시간 외 방문 (데이터 있는 경우) |
| `OPENING_HOURS_UNVERIFIED` [FIX-28] | ℹ️ 회색 | 영업시간 정보 미확인 — 사용자 확인 권장 |
| `LATE_SCHEDULE` | 🌙 노랑 | 22:00 이후 시작 |
| `OUTLIER_FULL_DAY` | 📍 파랑 | 이상치 장소가 하루 통째로 차지 |

---

## 7. Plan B (USR-018 + USR-031)

> **v2.3 신규 도입.**
> 여행 전 일정 다듬기(USR-018)와 여행 중 응급 대응(USR-031)을 단일 로직으로 통합.
> "버리려는 장소의 위경도"를 기준으로 altPool에서 1km 2단계 폭포수 검색.

### 7.1 핵심 설계 원칙

1. **단일 로직** — 여행 전/중 구분 없이 동일 처리. 사용자가 누른 시점만 다름.
2. **DB 좌표 기준** — 실시간 GPS 미사용. 교체 대상 장소의 DB 위경도를 기준점으로 사용.
   - GPS 권한 불필요 / 응답 속도 향상 / 원래 동선 유지
3. **altPool 우선, API는 명시적 진입 시에만** — 자동 외부 API 호출 X. 사용자가 [지도 검색] 누를 때만 호출.
4. **RESTRUCTURE 트랜잭션 재활용** — 별도 추천 시스템 X. 기존 수동편집 로직 활용.
5. **Pool Swap** — 방출된 장소는 altPool로 복귀. 사용자 변심 시 재사용 가능.

### 7.2 트리거 및 UX

```
일정표 슬롯 길게 누르기 (Long Press)
  ↓
바텀 시트 팝업
  ┌─────────────────────────────────────┐
  │  ○○라멘 대신 다른 장소               │
  │                                       │
  │  [같은 종류 추천 — N개]               │
  │                                       │
  │  ┌───────────────────────┐           │
  │  │ 1. △△식당             │ [선택]  │
  │  │    ⭐4.5 / 도보 8분 / FOOD       │
  │  ├───────────────────────┤           │
  │  │ 2. ◇◇식당             │ [선택]  │
  │  │    ⭐4.3 / 도보 12분             │
  │  └───────────────────────┘           │
  │                                       │
  │  ━━━━━━━━━━━━━━━━━━━━━━              │
  │  [🔍 지도에서 새로 검색]              │
  └─────────────────────────────────────┘
```

### 7.3 의사코드 — 추천 검색

```
FUNCTION plan_b_recommend(group, slot_to_replace):
  
  // 교체 대상 장소 정보 추출
  target_place = slot_to_replace.place
  target_pos   = {lat: target_place.latitude, lng: target_place.longitude}
  target_cat   = target_place.category
  
  // ─────────────────────────────────────
  // 1km 2단계 폭포수 검색 [FIX-31]
  // 같은 카테고리만 (사용자 의도 존중)
  // 다른 카테고리 원하면 [지도 검색] 버튼 사용
  // ─────────────────────────────────────
  altPool = group.altPool   // schedule_alts 테이블 조회
  
  // 1순위: 같은 카테고리 + 1km 이내
  tier1 = altPool
    .filter(category == target_cat
            AND haversine(target_pos, alt) <= PLAN_B_NEAR_RADIUS_KM)
    .sort(priority_score DESC,
          haversine(target_pos, alt) ASC,
          place_id ASC)
  
  results = tier1.take(PLAN_B_MAX_RESULTS)
  
  // 2순위: 부족하면 같은 카테고리 + 거리 무관
  IF results.count < PLAN_B_MAX_RESULTS:
    needed = PLAN_B_MAX_RESULTS - results.count
    
    tier2 = altPool
      .filter(category == target_cat
              AND haversine(target_pos, alt) > PLAN_B_NEAR_RADIUS_KM)
      .sort(priority_score DESC,
            haversine(target_pos, alt) ASC,
            place_id ASC)
      .take(needed)
    
    results.addAll(tier2)
  
  RETURN {
    candidates: results,
    target_place: target_place,
    has_more_via_search: TRUE   // 항상 [지도 검색] 진입 가능
  }
```

### 7.4 의사코드 — 교체 적용 (RESTRUCTURE 재활용)

```
FUNCTION plan_b_apply(group, slot_to_replace, selected_place):
  
  // ─────────────────────────────────────
  // 1. Pool Swap [FIX-33]
  //    방출된 장소 → altPool 복귀
  //    선택된 장소 → altPool에서 제거
  // ─────────────────────────────────────
  old_place = slot_to_replace.place
  
  // 새 장소 처리
  IF selected_place.from_external_search:
    // 지도 검색에서 가져온 새 장소 (places 테이블에 없을 수 있음)
    new_place_id = places.upsert(selected_place)
    // ※ altPool에는 추가하지 않음 (투표 통과 안 했으므로)
  ELSE:
    // altPool에서 선택
    new_place_id = selected_place.place_id
    altPool.remove(selected_place)
  
  // 방출된 장소 altPool 복귀 (아직 그 장소가 mainPool에 다른 슬롯으로 남아있지 않을 때만)
  IF NOT schedules.exists(group_id, place_id = old_place.id):
    altPool.add(old_place)
  
  // ─────────────────────────────────────
  // 2. RESTRUCTURE 트랜잭션 재활용
  //    - 슬롯 교체
  //    - 해당 Day TSP 재계산 (시간 재할당)
  //    - 경고 배지 갱신
  // ─────────────────────────────────────
  day = slot_to_replace.day_number
  
  // 슬롯 교체
  slot_to_replace.place_id = new_place_id
  
  // 해당 Day의 모든 슬롯을 가져와 RESTRUCTURE
  day_places = schedules.filter(group_id, day_number = day)
                        .map(slot.place)
  
  new_schedule = step3_simple_tsp(
    clusters      = {day: day_places},
    K             = 1,
    accommodation = group.accommodation,
    destination   = group.destination,
    travel_style  = group.travel_style
  )
  schedules.update(day, new_schedule)
  
  // ─────────────────────────────────────
  // 3. WebSocket 브로드캐스트
  // ─────────────────────────────────────
  BROADCAST schedules TO all_members
  
  // 그룹 알림 (여행 중인 경우 다른 멤버 안내)
  NOTIFY all_members "{editor.name}님이 일정을 변경했어요"
```

### 7.5 [지도 검색] Fallback [FIX-34]

```
사용자가 바텀시트의 [🔍 지도에서 새로 검색] 클릭
  ↓
USR-007 (장소 검색) 화면으로 이동
  ↓
카카오맵 / 구글 Places API 호출 (이때만 외부 API 사용)
  ↓
사용자가 새 장소 선택
  ↓
plan_b_apply(group, slot, selected_place)
  with selected_place.from_external_search = TRUE
```

**API 호출 정책:**
- 자동 호출 X — 항상 사용자가 [지도 검색] 명시적으로 누를 때만
- altPool이 부족해도 자동 fallback 안 함 (사용자 결정)

### 7.6 Plan B 동작 시나리오

#### 시나리오 A: 여행 전, 박물관 슬롯 마음에 안 듦

```
사용자: 박물관 슬롯 길게 누름

추천 (1km 2단계 폭포수):
  1순위 (같은 카테고리 + 1km): 시립미술관, 갤러리 X → 2개
  2순위 (같은 카테고리 + 거리 무관): 외곽 박물관 → 1개
  → 총 3개 추천 + [지도 검색] 버튼

사용자: 시립미술관 선택
→ RESTRUCTURE 자동 실행
→ Day 시간 재계산
→ 박물관 → altPool 복귀
```

#### 시나리오 B: 여행 중, 라멘집 임시휴무

```
사용자가 ○○라멘집 도착, 임시휴무 발견
  ↓
앱에서 라멘집 슬롯 길게 누름
  ↓
추천 (1km 2단계 폭포수):
  1순위 (FOOD + 1km): 분식 E, 한식당 A → 2개
  2순위 (FOOD + 거리 무관): 양식당 D, 중식당 C → 2개
  → 총 4개 추천 + [지도 검색] 버튼

사용자: 한식당 A 선택
→ Day TSP 재계산 (라멘집 자리에 한식당 A)
→ WebSocket으로 그룹 전원 알림: "○○님이 일정을 변경했어요"
```

#### 시나리오 C: altPool 비어있는 외곽 여행

```
사용자: 박물관 슬롯 길게 누름

추천 (폭포수): 0개
  → "추천 가능한 후보가 없어요" 메시지
  → [🔍 지도에서 새로 검색] 버튼만 표시

사용자: [지도 검색] 클릭
→ 카카오맵 API 호출 (이때만)
→ 검색 결과에서 새 장소 선택
→ places 테이블에 INSERT
→ Plan B Apply (RESTRUCTURE)
```

### 7.7 Plan B 정책 요약

| 항목 | 정책 |
|---|---|
| 트리거 | 슬롯 Long Press → 바텀시트 |
| 검색 기준 | 교체 대상 장소의 DB 위경도 |
| 검색 풀 | altPool (그룹 예비 후보) |
| 검색 방식 | 1km 2단계 폭포수 (같은 카테고리만) |
| 최대 후보 | 7개 |
| Fallback | [지도 검색] 버튼 (사용자 명시적 클릭 시만 외부 API) |
| 교체 동작 | RESTRUCTURE 트랜잭션 재활용 |
| Pool Swap | 방출 장소 → altPool 복귀 / 선택 장소 → altPool 제거 |
| 새 장소 (외부 검색) | places INSERT, altPool에는 추가 X |
| 그룹 동기화 | WebSocket + In-App 알림 |

---

## 8. 숙소 정책

> **v2.4 신규 명세.** 그동안 DDL에만 컬럼이 있고 UX/정책이 미정의였던 숙소 처리를 정식 명세화.

### 8.1 핵심 정책

| 항목 | 정책 |
|---|---|
| **누가 입력?** | 방장(OWNER)만 |
| **언제 입력?** | 그룹 생성 시 (선택) 또는 투표 시작 전까지 |
| **어떻게?** | USR-007 (지도 검색) 재활용 |
| **변경 가능 시점** | PLANNING / TRAVELLING / DONE (모든 단계 가능) |
| **변경 불가 시점** | VOTING / GENERATING (투표/일정생성 진행 중) |
| **여행 중 변경** | ✅ 지원 (current_date 이후 day TSP 재계산) |
| **다중 숙소 (Day별)** | ❌ v1 미지원 (v2 확장 예정) |
| **알림** | 변경 시 `SCHEDULE_UPDATED`로 통합 (별도 type 미신설) |

### 8.2 알고리즘 영향

**Step 1 거리 페널티:**
- 숙소 좌표 사용 X
- destination 기준 (현재 그대로)
- 이유: 사용자가 좋아하는 장소를 단지 호텔에서 멀다고 탈락시키지 않기 위함

**Step 2 K-Means:**
- 숙소 좌표 사용 X
- centroid 초기화는 destination 중심
- 이유: 사용자가 호텔 위치를 의식적으로 선택했으므로, 시스템이 자동 보정하지 않음

**Step 3 출발점:**
```
IF accommodation.lat is NOT NULL:
  startPoint = accommodation
ELSE:
  startPoint = destination
```

매일 아침 09:00 startPoint에서 출발. 마지막 슬롯 후 호텔 복귀는 일정에 포함하지 않음 (사용자 자율).

### 8.3 여행 중 호텔 변경 흐름 [FIX-36]

```
방장이 [숙소 변경] 클릭 (TRAVELLING 상태)
  ↓
PUT /api/groups/{groupId}/accommodation
  - 권한 체크: role = OWNER
  - status 체크: VOTING/GENERATING이 아닌 경우만 허용
  ↓
서비스 레이어:
  1. groups.accommodation_* 업데이트
  2. 기존 schedules 조회 (existing_schedules)
  3. step3_simple_tsp(
       clusters,         // 이미 확정된 클러스터 그대로
       K,
       new_accommodation,
       destination,
       travel_style,
       start_date,
       current_date = TODAY,
       existing_schedules
     ) 호출
  4. current_date 이전 day는 보존 (FIX-36 로직)
  5. current_date 이후 day는 새 출발점으로 재계산
  6. DB UPDATE (변경된 day만)
  7. WebSocket 브로드캐스트
  8. 알림: "방장이 숙소를 변경했어요. 오늘 이후 일정이 재계산됐어요."
```

### 8.4 사용자 안내 메시지

여행 중 호텔 변경 시:
```
"숙소를 변경하면 {current_date}부터 {end_date}까지의 일정이 재계산됩니다.
 (이전 일정은 그대로 보존됩니다.)
 [확인] [취소]"
```

### 8.5 다중 숙소 (Day별) — v2 확장 예정

**검토 후 v1 미채택:**
- DDL 변경 부담 (별도 테이블 필요)
- UI/UX 신설 부담
- 현실 빈도 낮음 (대부분 단일 호텔)
- 호텔 변경 기능으로 부분 커버 가능

**v2에서 확장 시:**
- `group_accommodations` 테이블 신설 검토
- Step 3에서 `day_start_lat/lng` 우선 사용

---

## 9. 투표/Ready 정책 [FIX-39]

> **v2.4 신규 명세.** 알고리즘 진입 조건과 그룹 라이프사이클 명확화.

### 9.1 Ready 정의

| 조건 | 정책 |
|---|---|
| **Ready 누르려면** | 장바구니 1개 이상 |
| **Ready 클릭 시** | "한 번 누르면 취소할 수 없습니다" 팝업 → 확인 시 `is_ready=TRUE` |
| **Ready 해제** | 불가 (한 번 확정) |
| **장바구니 0개일 때 Ready 버튼** | 비활성화 |

### 9.2 자동 투표 시작

```
조건: 모든 멤버 is_ready = TRUE
동작: 즉시 status = VOTING 전이
알림: 모두에게 In-App 알림 (VOTE_STARTED)
화면: 모든 멤버 자동으로 투표 화면 전환
```

### 9.3 강제 투표 시작 (방장)

**조건:**
- 방장(OWNER) 권한
- 그룹 전체 장바구니 1개 이상 담김

**경고창 (방장에게 표시):**
```
조건: 미Ready 멤버 중 장바구니 1개 이상 담은 사람 존재

화면:
  ┌──────────────────────────────────┐
  │ ⚠️ 잠깐!                          │
  │                                   │
  │ {N}명이 아직 장소를 담는 중이에요. │
  │ ({이름1}, {이름2}, ...)           │
  │                                   │
  │ 지금 시작하면 더 못 담아요.         │
  │                                   │
  │ [그래도 시작]    [기다리기]        │
  └──────────────────────────────────┘
```

**[그래도 시작] 클릭:**
```
status = VOTING 전이
group_vote_info.is_force_started = TRUE 기록
알림: 모두에게 VOTE_STARTED
화면 전환:
  - Ready 멤버: 즉시 투표 화면
  - 미Ready 멤버: "투표가 시작됐어요" 모달 → 확인 → 투표 화면
```

### 9.4 투표 단계 정책 (단계 분리)

| 단계 | 가능 행동 | 불가 행동 |
|---|---|---|
| **PLANNING** | 장바구니 추가/삭제, Ready | 투표 X |
| **VOTING** | 스와이프 투표만 | 장바구니 추가 X |
| **GENERATING** | 대기 (로딩) | — |
| **TRAVELLING** | 일정 보기, 수동편집, Plan B, 가계부, 앨범 | 새 투표 X |
| **DONE** | 일정 보기, 정산 마무리 | 수정 X |

**즉, PLANNING과 VOTING은 동시 진행되지 않음.** 강제 시작 후 미담긴 멤버는 더 이상 담을 수 없음.

### 9.5 미투표자 / 본인 자동 LIKE

**미Ready인 사람의 본인 장바구니 처리:**
- `votes` 테이블에 `result=0` (자동 LIKE) INSERT
- 일반 LIKE와 동일하게 카운트
- Ready 여부 무관

**투표 표명자 분모 (FIX-15):**
```sql
total_voters_for_place = COUNT(votes WHERE place_id=X AND result IN (-1, 0, 1))
like_rate = LIKE_count / total_voters_for_place
```

미투표자(votes에 row 없음)는 분모에서 제외 = 페널티 없음.

### 9.6 투표 종료 조건

**자동 종료:**
1. 모든 멤버가 모든 장소에 대해 투표 완료
2. 또는 강제 시작 후 1시간 경과

**수동 종료:**
- 방장 [투표 마감] 버튼

**종료 시:** `group_vote_info.vote_ended_at` 기록 → 자동으로 `status = GENERATING` → 알고리즘 실행 → `status = TRAVELLING`

### 9.7 멤버 가입/탈퇴 정책

| 단계 | 가입 | 탈퇴 |
|---|---|---|
| **PLANNING** | ✅ 자유 가입 | ✅ 자유 |
| **VOTING / GENERATING** | ❌ 차단 | ⚠️ 가능, total_members 그대로 유지 (이미 시작된 투표 결과 보존) |
| **TRAVELLING / DONE** | ✅ **권한 제한 가입** | ✅ 자유 |

**TRAVELLING/DONE 가입자의 권한 제한:**
- ✅ 일정/지도 보기, 가계부 참여, 앨범 업로드, 수동편집, Plan B
- ❌ 투표 / 장바구니 추가 (이미 끝난 단계)
- 알고리즘 영향 없음 (Step 1~3 재실행 안 하므로)

---

## 10. 지도 표시 정책 [FIX-37, FIX-38]

> **v2.4 신규 명세.** Wanderlog 스타일 참고, 외부 길찾기 앱 위임.

### 10.1 마커 표시

**Day 탭 전환 방식:**
```
상단 탭: [Day 1] [Day 2] [Day 3] [Day 4]
선택한 day의 마커만 표시 (전체 표시 X)
```

**마커 디자인:**
- 형태: 번호 + 카테고리 아이콘 (단색)
- 예: `[1☕]` `[2🏛]` `[3🍴]` `[4🛍]` `[5🌳]`
- 색상은 단일 (카테고리별 색상 구분 X)
- 번호는 day 내 방문 순서 (slot_order)

### 10.2 마커 클릭 시 하단 시트

```
┌─────────────────────────────────┐
│  ☕ 카페 ABC                    │
│  ⭐ 4.5                         │
│                  [썸네일 사진]   │
│                                 │
│  [세부 정보 →]                  │  ← 외부 링크
│  [🗺 길찾기]                    │
└─────────────────────────────────┘
```

**표시 정보:**
- 장소명
- 카테고리 아이콘
- 평점 (있는 경우)
- 썸네일 사진 (있는 경우)

**미표시 정보:**
- 도착 시각, 체류시간 (일정표에 있으므로 중복 X)
- 영업시간 (외부 앱에서 확인)

**버튼:**
- `[세부 정보 →]`: 외부 링크 (장소 상세 페이지)
- `[🗺 길찾기]`: 외부 길찾기 앱 호출

### 10.3 경로 라인

**표시 안 함.**
- 마커만 표시
- 직선 폴리라인 X (강 가로지르는 직선이 어색함)
- 실제 도로 라인 X (길찾기 API 호출 비용 부담)

### 10.4 외부 길찾기 앱 연동 [FIX-38]

```
[길찾기] 클릭 시:

IF group.is_overseas == TRUE:
  → 구글맵 바로 실행
  → intent: google.navigation:q={lat,lng}
  → 출발지/도착지 자동 입력 (이전 슬롯 → 현재 슬롯)

ELSE (국내):
  → 다이얼로그 표시
  ┌─────────────────────────┐
  │ 어떤 지도로 보시겠어요?  │
  │ [카카오맵]  [구글맵]    │
  └─────────────────────────┘
  → 사용자 선택 → 해당 앱 실행
```

**근거:**
- 해외 = 카카오맵 데이터 빈약 → 구글맵 강제
- 국내 = 카카오/구글 양쪽 다 좋음 → 사용자 선호 존중

### 10.5 통합 길찾기 / altPool 마커

| 항목 | 정책 |
|---|---|
| 통합 "Day 전체 길찾기" | ❌ 미지원 (슬롯별만) |
| altPool 마커 표시 | ❌ 미표시 (Plan B 호출 시에만 보임) |

---

## 11. 알고리즘 입출력 명세 [FIX-41]

> **v2.4 신규.** 팀원이 백엔드 API 만들 때 알고리즘과 정확히 합쳐지도록 함수 시그니처 명세.

### 11.1 설계 원칙

- 모든 알고리즘 함수는 **순수 함수** (사이드 이펙트 X)
- DB 조회/저장은 **서비스 레이어** 책임
- 알고리즘은 입력을 받아 출력만 반환

### 11.2 `step1_weighted_cost`

**입력:**
```typescript
{
  group: {
    id, destination_lat, destination_lng,
    travel_style: 'RELAXED' | 'PACKED',
    start_date, end_date, is_overseas
  },
  members: Array<{ user_id, role, is_ready }>,
  places: Array<{
    place_id, bookmarked_by, name, category,
    density_point, estimated_duration,
    latitude, longitude
  }>,
  votes: Array<{ place_id, user_id, result }>  // result: 1 / -1 / 0
}
```

**출력:**
```typescript
{
  mainPool: Array<{
    place_id, priority_score, vote_score,
    like_count, dislike_count,
    distance_to_destination_km,
    is_outlier_candidate
  }>,
  altPool: Array<{
    place_id, priority_score, vote_score, like_count
  }>,
  meta: {
    K,                       // 여행 일수
    total_members,
    passed_threshold,        // CEIL(N × 0.5)
    density_limit,           // 5(RELAXED) / 8(PACKED)
    food_per_day_quota,      // 1 / 2
    max_dist_km,
    destination: { lat, lng }
  }
}
```

### 11.3 `step2_kmeans`

**입력:**
```typescript
{
  mainPool, altPool, meta   // step1Output 그대로
}
```

**출력:**
```typescript
{
  clusters: Map<int, Array<Place>>,    // day(1~K) → 장소 배열
  centroids: Map<int, { lat, lng, day }>,
  altPool: Array<Place>,                // density 강등 추가된 최종
  warnings: Array<{ place_id, code: 'OUTLIER_FULL_DAY' }>
}
```

### 11.4 `step3_simple_tsp`

**입력:**
```typescript
{
  clusters,
  K,
  group: {
    accommodation: { lat, lng } | null,
    destination: { lat, lng },
    travel_style,
    start_date
  },
  current_date?: date | null,            // [FIX-36] 호텔 변경 시
  existing_schedules?: DaySchedule[] | null
}
```

**출력:**
```typescript
{
  schedules: Array<{
    day_number, day_date,
    slots: Array<{
      slot_order, place_id,
      start_time, duration_minutes, travel_time_minutes,
      warnings: Array<{ code }>
    }>
  }>
}
```

### 11.5 `restructure_day` (REORDER/RESTRUCTURE) [FIX-45]

> **레이어 구분 (v2.5 명확화):**
>
> | 함수 | 레이어 | 역할 |
> |---|---|---|
> | **`restructure_day`** (이 §) | **알고리즘 레이어** | 순수 함수. 입력 받아 day_schedule 출력. 사이드 이펙트 X |
> | **`recompute_reorder`** (§6.4) | **편집 세션 컨트롤러 레이어** | 그룹 락 / DB 업데이트 / WebSocket 브로드캐스트 처리 |
>
> **흐름:**
> ```
> 사용자 Drag&Drop
>   ↓
> recompute_reorder (§6.4) — 편집 세션 시작
>   ↓
> restructure_day (이 §) — 알고리즘 호출
>   ↓
> recompute_reorder — DB 저장 + 알림
> ```
>
> **사용자/프론트엔드는 `recompute_reorder` 호출, 내부적으로 `restructure_day` 사용.**

**입력:**
```typescript
{
  group: { accommodation, destination, travel_style, start_date },
  day_number,
  day_date,
  places: Array<Place>,                  // 변경된 장소 집합
  user_ordered?: bigint[] | null         // REORDER 시 사용자 지정 순서
                                         // null이면 RESTRUCTURE (TSP 자동 정렬)
}
```

**출력:**
```typescript
{
  day_schedule: DaySchedule
}
```

### 11.6 `plan_b_recommend`

**입력:**
```typescript
{
  target_place: { place_id, latitude, longitude, category },
  altPool: Array<{
    place_id, latitude, longitude, category, priority_score
  }>
}
```

**출력:**
```typescript
{
  candidates: Array<{
    place_id,
    distance_km,
    tier: 1 | 2          // 1순위(1km 이내) / 2순위(거리 무관)
  }>
  // 최대 7개
}
```

### 11.7 백엔드 서비스 레이어 흐름 (참고)

```
function generateSchedule(groupId):
  // 1. DB 조회
  group, members, places, votes = db.find(groupId)
  
  // 2. 알고리즘 실행 (순수 함수)
  s1 = step1_weighted_cost({ group, members, places, votes })
  s2 = step2_kmeans({ ...s1 })
  s3 = step3_simple_tsp({ clusters: s2.clusters, K: s1.meta.K, group, ... })
  
  // 3. DB 저장 (트랜잭션)
  db.transaction:
    insert schedules from s3
    insert schedule_alts from s2.altPool
    update group.status = TRAVELLING
  
  // 4. 브로드캐스트
  ws.broadcast(groupId, 'SCHEDULE_GENERATED', s3.schedules)
```

### 11.8 입출력 명세 활용

- 팀원이 백엔드 API 작성 시 이 명세를 계약(contract)으로 사용
- 알고리즘 구현자(Min)와 백엔드 구현자가 인터페이스만 합의되면 병렬 작업 가능
- 테스트 시 명세대로 mock 데이터 만들어 검증

---

## 12. 헬퍼 함수

### 12.1 Haversine 거리

```
FUNCTION haversine(p1, p2):
  lat1_rad = p1.lat × PI / 180
  lat2_rad = p2.lat × PI / 180
  dlat     = (p2.lat - p1.lat) × PI / 180
  dlng     = (p2.lng - p1.lng) × PI / 180
  
  a = sin(dlat/2)² + cos(lat1_rad) × cos(lat2_rad) × sin(dlng/2)²
  c = 2 × atan2(sqrt(a), sqrt(1-a))
  
  RETURN EARTH_RADIUS_KM × c  // km 단위
```

### 12.2 영업시간 구간 체크 [FIX-40]

> **v2.4 정책 변경:** 영업시간 기능은 **해외 전용**.
> 카카오 로컬 API는 영업시간을 제공하지 않으므로 국내 장소는 항상 `opening_hours = NULL`.
> 해외 장소는 구글 Places Details API에서 정규화하여 저장.

```
FUNCTION place.is_open_during(arrival_time, departure_time):
  // arrival부터 departure까지 연속으로 영업 중인지 확인
  
  day_of_week = current_date.day_of_week (MON/TUE/...)
  hours = place.opening_hours[day_of_week]
  
  // 데이터 없으면 통과 (관대) + UI는 OPENING_HOURS_UNVERIFIED 배지 처리
  IF place.opening_hours is NULL:
    RETURN TRUE
  
  // 휴무일 (빈 배열)
  IF hours is NULL OR hours.isEmpty:
    RETURN FALSE   // 명확한 휴무
  
  FOR each (open, close) IN hours:
    IF arrival_time >= open AND departure_time <= close:
      RETURN TRUE
  
  RETURN FALSE
```

### 영업시간 JSON 스키마 (확정)

```json
{
  "MON": [{"open": "09:00", "close": "22:00"}],
  "TUE": [
    {"open": "10:00", "close": "14:00"},
    {"open": "17:00", "close": "22:00"}
  ],
  "WED": [{"open": "09:00", "close": "22:00"}],
  "THU": [{"open": "09:00", "close": "22:00"}],
  "FRI": [{"open": "09:00", "close": "23:00"}],
  "SAT": [{"open": "10:00", "close": "23:00"}],
  "SUN": []
}
```

**특수 케이스:**

| 케이스 | 표현 |
|---|---|
| 24시간 영업 | `{"open":"00:00","close":"24:00"}` |
| 자정 넘김 (예: 18:00~02:00) | `{"open":"18:00","close":"23:59"}` (단순화) |
| 정기 휴무 | `[]` (빈 배열) |
| 영업시간 정보 자체 없음 | `opening_hours` 컬럼 NULL |

**경고 배지 정책:**

| 상황 | 배지 |
|---|---|
| 국내 (is_overseas=FALSE) | `OPENING_HOURS_UNVERIFIED` 표시 안 함 (모든 장소 NULL이라 의미 없음) |
| 해외 + opening_hours NULL | `OPENING_HOURS_UNVERIFIED` 표시 |
| 해외 + 데이터 있음 + 영업 외 | `OUTSIDE_OPENING_HOURS` 표시 |


### 12.3 편집 유형 판별 (프론트엔드)

```
FUNCTION detect_edit_type(original_places, new_places, original_order, new_order):
  
  original_ids = SET(p.id for p IN original_places)
  new_ids      = SET(p.id for p IN new_places)
  
  places_changed = (original_ids != new_ids)
  order_changed  = (original_order != new_order)
  
  IF places_changed AND order_changed:
    RETURN 'MIXED'
  ELSE IF places_changed:
    RETURN 'RESTRUCTURE'
  ELSE IF order_changed:
    RETURN 'REORDER'
  ELSE:
    RETURN 'NO_CHANGE'
```

---

## 13. 패치 변경 이력

### 13.1 v2.5 → v2.6 패치 1건 (FOOD 동선 개선)

| ID | 위치 | 변경 내용 | 유형 |
|---|---|---|---|
| **FIX-47** | §5.4 헬퍼 | `insert_food_by_window` — FOOD 선택 방식을 `food_idx` 순 고정 → 삽입 직전 위치 기준 nearest 선택으로 교체. `consumed` 플래그 도입. | 알고리즘 변경 |

> **변경 배경:** FOOD를 priority 순으로 고정 선택하면 삽입 위치와 지리적으로 무관한 식당이 배정되어 동선 지그재그가 발생할 수 있음. Step 2에서 이미 Day 권역 기준으로 FOOD가 배정돼 있으므로, 동일 권역 내에서 삽입 직전 위치와 가장 가까운 FOOD를 선택하는 방식으로 교체하여 동선 품질 개선.

### 13.2 v2.4 → v2.5 패치 5건 (중간 보고서 검토 반영 — 문서 명확화)

| ID | 위치 | 변경 내용 | 유형 |
|---|---|---|---|
| **FIX-42** | §5.4 헬퍼 | `insert_food_by_window` 명확화 — `simulate_arrivals`/`prev_departure` 정의, 첫 슬롯 처리 명시 | 문서 명확화 |
| **FIX-43** | §2 공통 상수 | RELAXED 점심 부재 의도 주석 추가 (저녁 한 끼만 명시 슬롯) | 의도 명시 |
| **FIX-44** | §4.3 §2-7 로드밸런싱 | over_day지만 이동 대상 없는 엣지 케이스 인지 명시 | 엣지 케이스 |
| **FIX-45** | §11.5 입출력 명세 | `restructure_day`(알고리즘) vs `recompute_reorder`(편집 컨트롤러) 레이어 구분 | 레이어 정의 |
| **FIX-46** | 전역 3군데 | "60% 감소" 추정치 → "대폭 감소 예상" 표현 완화 | 표현 안전화 |

> **알고리즘 로직 변경 X.** 모두 문서/표현 명확화. 검토자 지적 반영하여 백엔드 구현 시 모호함 제거 + 보고서 디펜스 안정성 확보.

### 13.3 v2.3 → v2.4 패치 7건 (숙소/지도/투표/입출력 통합)

| ID | 위치 | 변경 내용 | 유형 |
|---|---|---|---|
| **FIX-35** | §8 숙소 정책 | 방장만 입력, 투표 전 자유 변경, 여행 중에도 변경 가능 | 신규 명세 |
| **FIX-36** | §5 Step 3 시그니처 | `current_date`/`existing_schedules` 파라미터 추가 (호텔 변경 시 부분 재계산) | 알고리즘 변경 |
| **FIX-37** | §10 지도 표시 | Day 탭 / 카테고리 아이콘+번호 / 마커만 (경로 라인 X) | UX 정의 |
| **FIX-38** | §10 길찾기 외부 앱 | 해외=구글맵 강제 / 국내=선택 다이얼로그 | UX 정의 |
| **FIX-39** | §9 투표/Ready | Ready 취소 불가 / 강제시작 경고창 / 단계 분리 / TRAVELLING 멤버 가입 권한 제한 | 신규 명세 |
| **FIX-40** | §12.2 영업시간 | 해외 전용 (국내는 카카오 API 미제공으로 NULL) | 정책 변경 |
| **FIX-41** | §11 입출력 명세 | Step 1~3 / restructure / Plan B 함수 시그니처 명세 | 신규 문서 |

> **검토 후 미채택:** 다중 숙소(Day별)는 v2 확장 / Plan B 외부 API 자동 호출 거부 / 자유시간 슬롯 자동 추가 거부 (모두 단순화 철학 유지)

### 13.4 v2.2 → v2.3 패치 5건 (Plan B 정식 도입)

| ID | 위치 | 변경 내용 | 유형 |
|---|---|---|---|
| **FIX-30** | §7 Plan B 신설 | USR-018 + USR-031 통합, 단일 로직으로 처리 | 신규 기능 |
| **FIX-31** | §7.3 추천 검색 | 1km 2단계 폭포수 (같은 카테고리만) | 신규 기능 |
| **FIX-32** | §7.2 트리거 | 슬롯 Long Press → 바텀시트 | UX 정의 |
| **FIX-33** | §7.4 Pool Swap | 방출 장소 → altPool 복귀 정책 | 정책 정의 |
| **FIX-34** | §7.5 Fallback | 외부 API는 [지도 검색] 명시적 진입 시에만 호출 | 비용 정책 |

### 13.5 v2.1 → v2.2 패치 9건 + Step 3 단순화 (Step 2/3 검증)

| ID | 위치 | 변경 내용 | 유형 |
|---|---|---|---|
| **FIX-18** | Step 2-§2-1.5 가드 절 | `non_food < K` 시 K-Means 스킵, 직접 분배 | 안전 보강 |
| **FIX-19** | Step 2-§2-2 centroid radius | `5km` 고정 → `max_dist / 2` 동적 (최소 1km) | 효율 개선 |
| **FIX-20** | Step 2-§2-4 FOOD 배정 | 순환 → 거리 기반 (가까운 centroid의 day) | 동선 개선 |
| **FIX-21** | Step 2-§2-7 로드밸런싱 | 임계값 `> avg_ceil + 1` → `> avg_ceil` | 정확성 |
| **FIX-22** | Step 2-§2-3 클러스터 배정 | centroid 동률 시 `day ASC` tie-break | 결정론성 |
| **FIX-23** | Step 2-§2-3 수렴 조건 | `< 0.01km / 100회` → `< 0.05km / 30회` | 효율 개선 |
| **FIX-24** | Step 2-§2-6 폐지 | mainPool 보충 로직 제거 | 단순화 |
| **FIX-25** | Step 3 NN 정렬 | 거리 동률 시 `priority DESC, place_id ASC` tie-break | 결정론성 |
| **FIX-28** | Step 3 / REORDER 경고 | `OPENING_HOURS_UNVERIFIED` 배지 신설 | 안내 강화 |
| **— Step 3 전면 단순화** | Step 3 전체 | 영업시간 체크 / altPool 자동 대체 / 자유시간 슬롯 / 21:00 상한 모두 폐지 | 정책 변경 |

> **폐지된 검토안 (FIX-26, 27, 29):** Step 3 단순화에 흡수되어 별도 패치 불요.
> **검토 후 미채택:** 여행 스케일별 파라미터 차등화 (자동 판별이 철학과 충돌), 자차 SPEED 분기 (도보/대중교통만 지원 결정).

### 13.6 v2.0 → v2.1 패치 7건 (Step 1 검증)

| ID | 위치 | 변경 내용 | 유형 |
|---|---|---|---|
| **FIX-11** | Step 1-§3 altPool | 싫어요 테러 컷오프: `vote_score > 0` 조건 추가 | 정책 변경 |
| **FIX-12** | Step 1-§5 mainPool | `priority_score > 0` 가드 — 음수 점수 mainPool 차단 | 정책 변경 |
| **FIX-13** | Step 1 전역 정렬 | 동점자(Tie-breaker) 4단계 명시: priority → like → dist → id | 결정론성 보장 |
| **FIX-14** | Step 1-§2 거리 정규화 | `max_dist < 3km` 시 페널티 무시 (우물 안 개구리 방지) | 버그 수정 |
| **FIX-15** | Step 1-§1 vote_score | 미투표 처리 — 투표 표명자 기준 정규화 (`total_voters` 분모) | 정책 변경 |
| **FIX-16** | Step 1 시그니처 | K 계산식 명시: `(end_date - start_date).days + 1` | 누락 보완 |
| **FIX-17** | Step 1-§6 이상치 | 절대 거리 기반 임계값 (`OUTLIER_DIST_KM=30`) | 버그 수정 |

### 13.7 v1.0 → v2.0 패치 10건

| ID | 위치 | 변경 내용 | 유형 |
|---|---|---|---|
| **FIX-1** | Step 1 vote_score | `bonus_rate` 제거 (본인 자동 LIKE로 자연 반영) | 정책 변경 |
| **FIX-2** | Step 1 altPool | 비율 기준 → 절대값 기준 전환 | 정책 변경 |
| **FIX-3** | REORDER 신설 | 수동편집 단순 경고 모드 도입 | 정책 변경 |
| **FIX-4** | Step 2 centroid 초기화 | 라디안 변환 + cos(lat) 경도 보정 | 버그 수정 |
| **FIX-5** | Step 2 load balancing | density 제약 재검증 추가 | 버그 수정 |
| **FIX-6** | Step 3 FOOD ELSE 브랜치 | used_windows / food_places / current_time 3줄 누락 패치 | 버그 수정 |
| **FIX-7** | Step 3 전역 영업시간 | `is_open(time)` → `is_open_during(arrival, departure)` 구간 체크 | 버그 수정 |
| **FIX-8** | Step 3 하루 상한 | `DAY_END_TIME=21:00` 도입, 초과 시 altPool 강등 | 버그 수정 |
| **FIX-9** | 공통 상수 | `SPEED_KMH=25` 명시 정의 | 누락 보완 |
| **FIX-10** | 전역 예외 처리 | max_dist=0 / 빈 클러스터 / avg FLOOR·CEIL 경계 | 누락 보완 |

> ※ v2.2에서 FIX-7, 8은 Step 3 전면 단순화로 폐지됨 (영업시간 체크 / 21:00 상한 모두 제거).

### 13.8 정책 결정 히스토리

| 결정 | v1.0 | v2.0 | v2.1 | v2.2 | v2.3 | v2.4 | v2.6 (현재) | 근거 |
|---|---|---|---|---|---|---|---|---|
| **bonus_rate** | 포함 | 제거 | 유지 | 유지 | 유지 | 유지 | 유지 | 본인 자동 LIKE로 double counting 방지 |
| **altPool 기준** | 비율 | 절대값 | + `vote_score > 0` | 유지 | 유지 | 유지 | 유지 | 그룹 크기 무관 + 싫어요 테러 차단 |
| **Step 3 영업시간 체크** | — | `is_open` | `is_open_during` | **폐지** | 폐지 | 폐지 | 폐지 | 데이터 불완전 + 사용자 자율 |
| **Step 3 21:00 상한** | — | 도입 | 유지 | **폐지** | 폐지 | 폐지 | 폐지 | Density가 일정량 제어 |
| **여행 스케일 차등화** | — | — | — | **불채택** | 불채택 | 불채택 | 불채택 | 자동 추측 = 철학 위배 |
| **자차 SPEED 분기** | — | — | — | **불채택** | 불채택 | 불채택 | 불채택 | 도보/대중교통만 지원 |
| **USR-018, 031 분리** | — | — | — | 분리 | **통합** | 통합 | 통합 | 시점만 다름, 로직 동일 |
| **Plan B 폭포수 임계값** | — | — | — | — | **1km 2단계** | 유지 | 유지 | 도보 12분 = "근처" 직관 |
| **Plan B 외부 API** | — | — | — | — | **명시적 진입만** | 유지 | 유지 | 비용 0원, 사용자 자율 |
| **숙소 입력 권한** | — | — | — | — | — | **방장만** | 유지 | 그룹 공통 정보, 충돌 방지 |
| **여행 중 호텔 변경** | — | — | — | — | — | **지원** | 유지 | 응급 대응 + 동적 재계산 |
| **다중 숙소 (Day별)** | — | — | — | — | — | **v2 확장** | v2 확장 | DDL 부담 + 빈도 낮음 |
| **지도 경로 라인** | — | — | — | — | — | **표시 X** | 표시 X | 직선 어색, 길찾기 API 비용 |
| **길찾기 앱** | — | — | — | — | — | **외부 위임** | 외부 위임 | 비용 0, 사용자 친숙한 앱 |
| **영업시간 데이터** | — | — | — | — | — | **해외 전용** | 해외 전용 | 카카오 API 미제공 |
| **Ready 해제** | — | — | — | — | — | **불가** | 불가 | 한 번 누르면 확정, 단순화 |
| **TRAVELLING 멤버 가입** | — | — | — | — | — | **권한 제한 허용** | 유지 | 동행자 합류 현실 반영 |
| **FOOD 끼워넣기 선택** | — | — | — | priority 순 | priority 순 | priority 순 | **nearest 선택** | 동선 지그재그 방지 [FIX-47] |

---

## 14. 실구현 전 확인 필요 사항

### 14.1 우선순위 높음 (알고리즘 영역, Min 담당)

1. **백엔드 Category enum 정의** (알고리즘 입력 변환 레이어)
   ```java
   public enum Category {
     FOOD(0, 60),
     CULTURE(2, 90),
     ACTIVITY(3, 120),
     SHOPPING(1, 60),
     NATURE(2, 90),
     ETC(1, 60);
     
     private final int densityPoint;
     private final int estimatedDurationMinutes;
   }
   ```

2. **카카오/구글 카테고리 → SyncTrip Category 매핑 레이어** (Plan B 새 장소 INSERT 시)
   - 카카오: "음식점 > 한식 > 비빔밥" → FOOD
   - 구글: "restaurant" → FOOD / "tourist_attraction" → CULTURE
   - 매핑 룰 정의 필요

3. **opening_hours 정규화 레이어** (해외 전용)
   - 구글 Places Details API → §12.2 JSON 스키마 변환
   - 자정 넘김 처리 (`23:59` 단순화)
   - 24시간 영업 (`00:00 ~ 24:00`)

### 14.2 우선순위 중간 (팀 협의 영역)

4. **Vision AI API 선정** (가계부 영수증 — 팀원 영역, GPT-4o vs Gemini Vision)
5. **MySQL 버전 확정** (CHECK 제약 강제 적용 → 8.0.16+)
6. **그룹 락 메커니즘 구현** (DB? Redis? WebSocket 세션?)
7. **`schedules.is_free_time` 활용 정책**
   - 알고리즘은 자동 생성 안 함 (v2.2 단순화)
   - 사용자 수동편집/Plan B 시에만 INSERT

---

## 15. 논문 어필 포인트

### 핵심 기여사항
1. **Weighted Cost Function with Normalization**
   - 인원 정규화된 `like_rate` 적용 (투표 표명자 기준)
   - 거리 페널티를 결합한 `priority_score`
   - 싫어요 테러 / 우물 안 개구리 왜곡 방어 메커니즘

2. **Density Score 기반 슬롯 편입**
   - 카테고리별 가중치 (ACTIVITY=3 / CULTURE,NATURE=2 / SHOPPING,ETC=1)
   - FOOD는 쿼터 별도 (Time Window 기반)
   - RELAXED(5) / PACKED(8) 2-모드 — 하루 일정량 자율 제어

3. **Balanced K-Means with Adaptive Centroid**
   - 360°/K 균등 분산 초기화 (위도 보정 cos(lat))
   - 동적 radius (`max_dist / 2`) — 다양한 여행 스케일 대응
   - 빈 클러스터 재배치 + Density 리밸런싱
   - Tie-breaker로 결정론적 재현성 보장

4. **Simple Order & Time TSP** (v2.2 핵심 통찰)
   - Step 1, 2가 합리적 후보를 선별했으므로 Step 3는 **단순 정렬 + 시간 할당**
   - FOOD를 Window 시간대 위치에 끼워넣는 경량 로직
   - FOOD 끼워넣기 시 삽입 직전 위치 기준 nearest 선택으로 동선 지그재그 방지 [FIX-47]
   - 영업시간/제약 검증은 알고리즘이 강제하지 않고 UI 경고로 위임

5. **편집 유형별 차등 재계산**
   - REORDER: 1-pass 이동시간 재계산
   - RESTRUCTURE: 단일 Day TSP 재실행
   - K-Means는 최초 1회만 (재실행 X)

6. **"분 단위 정확성보다 동선 가이드" 철학** ⭐ (v2.2 핵심)
   - 사용자는 알고리즘이 제시한 시간표를 분 단위로 따르지 않음
   - 일정표 = 정확한 타임테이블이 아니라 **동선 안내 가이드**
   - 미시 제약(영업시간 30분 초과 등)은 알고리즘이 잡지 않음
   - 모든 제약 위반은 **UI 경고 + 사용자 수동편집**으로 위임
   - 결과: 알고리즘 코드 대폭 감소 예상, 응답 속도 향상, 결정론성 보장

7. **"자동 생성 + 사용자 주도 조정" 투트랙 설계**
   - 알고리즘은 초안 제공, 제약은 기본값
   - 편집 단계에서는 차단 없이 경고 배지만
   - 사용자 자율성 극대화

### v2.2 단순화 인사이트 (논문 디펜스용)

> **"엔지니어링 판단력의 증명"**
>
> 본 시스템은 개발 과정에서 영업시간 자동 검증, altPool 자동 대체, 자유시간 슬롯 자동 추가 등 정교한 메커니즘을 도입했었다.
> 그러나 실제 여행 패턴 분석 결과, 사용자는 알고리즘이 제시한 시간표를 분 단위로 따르지 않으며 실시간 상황에 맞게 유연하게 조정한다는 점을 발견했다.
> 이에 따라 알고리즘이 모든 미시 제약을 강제하는 대신, 합리적 동선 가이드를 제공하고 사용자가 자율적으로 조정하도록 책임을 분리하는 단순화 결정을 내렸다.
> 이는 "복잡도와 효과의 trade-off"에 대한 엔지니어링 판단의 결과이며, 코드 단순화·결정론성 보장·응답 속도 향상이라는 부수 효과까지 가져왔다.

### v2.3 Plan B 설계 인사이트

> **"여행 전과 여행 중을 단일 로직으로 통합한 우아한 설계"**
>
> 일정 다듬기(여행 전)와 응급 대응(여행 중)은 표면적으로 다른 시나리오로 보이지만,
> 본질은 "슬롯 교체"라는 동일 작업이다.
> 두 기능을 별도 로직으로 분리하지 않고 단일 로직으로 통합하여 코드 복잡도를 낮췄다.
>
> 또한 외부 지도 API 호출을 자동화하지 않고 사용자가 [지도 검색]을 명시적으로 누를 때만 호출하도록 설계함으로써,
> API 비용을 0원에 가깝게 유지하면서도 사용자 의도를 명확히 반영했다.
>
> 여행 중 GPS 미사용 결정도 의도적이다.
> 교체 대상 장소의 DB 좌표를 기준으로 사용함으로써 권한 요청 부담 없이 원래 동선을 유지할 수 있도록 했다.

### v2.4 부분 재계산 / 단계 분리 인사이트

> **"여행 중 동적 일정 재계산 — 부분만 재계산"**
>
> 사용자가 여행 중 호텔을 변경할 수 있어야 한다는 요구가 있었다.
> 단순 구현은 K-Means부터 다시 돌리는 것이지만, 이는 K-Means 결정론성을 깨뜨리고 이미 지나간 일정까지 변경할 위험이 있다.
>
> 본 시스템은 Step 3 시그니처에 `current_date` 파라미터를 추가하여 **현재 시점 이전의 day는 보존하고 이후 day만 재계산**하는 우아한 설계를 채택했다.
> 이는 여행이라는 시계열 도메인에서 자연스럽고, 데이터 무결성도 보장한다.
>
> 또한 카카오 로컬 API가 영업시간 데이터를 제공하지 않는 현실 제약을 인식하고,
> "영업시간 기능 = 해외 전용"이라는 단순화 결정을 내렸다.
> 모든 도메인 문제를 알고리즘으로 해결하려 하지 않고, **데이터 가용성에 따라 기능 범위를 정직하게 제한**하는 엔지니어링 판단의 사례이다.

### Wanderlog 대비 차별점
- **그룹 투표 의사결정** (블라인드 스와이프)
- **AI 자동 초안 생성** (K-Means + Simple TSP)
- **사용자 주도 자율 편집** (제약 없는 Drag & Drop, 경고만 표시)
- **여행 전/중 통합 Plan B** (1km 2단계 폭포수, GPS 불필요, API 비용 0원)
- **여행 중 동적 일정 재계산** (호텔 변경 시 부분 재계산)
- **순수 함수 알고리즘 + DB 분리** (테스트 가능성, 결정론성 보장)

---

**문서 끝**
