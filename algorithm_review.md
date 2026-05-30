# SyncTrip 알고리즘 리뷰 — 잔여 과제

> 2026-05-30 세션에서 우선순위 1~6 항목 전부 구현 완료. 아래는 미완료 항목만 정리.

---

## 잔여 설계 이슈

### KMeans 초기 센트로이드 방식 불일치 (의도된 변경, 영향 존재)

- **코드 위치:** `KMeansClustering.java:71-98` (`initCentroids`)
- **현재 동작:** 결정론적 KMeans++ — 첫 센트로이드는 우선순위 최고 장소(`:77-79`), 이후는 최원거리 순 선택(`:81-96`). 주석에 명시된 의도적 교체.
- **잔존 문제:** 이상치(먼 장소)가 초기 센트로이드로 선택되면 그 장소 하나를 중심으로 day 하나가 형성됨. 도심 8곳 + 외곽 1곳 → 외딴 1개짜리 day 생성 가능.
- **판단:** 의도된 변경이므로 수정 여부는 정책 결정 사항.

---

## 잔여 로직 이슈

### Step2 FOOD 쿼터가 effectiveK에 연동되지 않음

- **코드 위치:** `WeightedCostFunction.java:113`, `KMeansClustering.java:181-193`
- **문제:** 전역 쿼터 `foodQuota = foodPerDay × K`. `effectiveK < K`(장소 부족)이면 cluster 쿼터 총합 < 전역 쿼터 → Step1을 통과한 FOOD가 Step2에서 overflow로 강제 탈락.
- **개선안:** Step2 FOOD 쿼터를 `effectiveK` 기준으로 재계산.
- **난이도:** 낮음

### effectiveK < K → 후행 빈 DayGroup, 프론트 처리 미정

- **코드 위치:** `KMeansClustering.java:45, 172, 203-214`
- **문제:** 장소 3개 + 5일 여행이면 4·5일차가 빈 `DaySchedule(day, List.of())`로 반환됨. 프론트가 빈 day를 "장소 추가 유도"로 보여줄지, 숨길지 정의 없음.
- **개선안:** `DAY_EMPTY` 플래그 추가 또는 서비스 레이어에서 "추천 장소 부족" 안내 데이터 첨부.
- **난이도:** 낮음

### rebalanceDensity의 List.remove — placeId 기준 명시 권장

- **코드 위치:** `KMeansClustering.java:270, 279`
- **현황:** `MainPoolPlace`는 record라 `equals`가 전체 컴포넌트 기준이므로 현재 실무상 안전. 단 방어적으로 `removeIf(p -> p.placeId() == place.placeId())` 권장.
- **난이도:** 낮음

### rebalanceLoad 1패스 후 불균형 잔존 가능

- **코드 위치:** `KMeansClustering.java:310-341`
- **문제:** density 리밸런싱(`:240` 수렴 루프)과 달리 load 리밸런싱은 단일 패스. 클러스터 인덱스 순서에 따라 받았다가 다시 내보내는 churn 발생, 1패스 후 불균형 잔존 가능.
- **개선안:** density처럼 "변화 없을 때까지(최대 N회)" 수렴 루프로 감싸거나, 단일 패스임을 주석으로 명시.
- **난이도:** 중간

---

## 잔여 UX 격차

### 이동시간 25km/h 고정 — Routes API 실측 미연동

- **현황:** `MIN_TRAVEL_MINUTES = 3` 하한은 적용됨. 단 25km/h 고정 추정 자체의 6배 오차(도보 0.5km 실제 7.5분 vs 코드 1.2분)는 미해결.
- **개선안:** 클러스터 내 NN 인접 구간만 Routes API로 실측 교체 (day당 N-1회 호출). 초안은 Haversine 유지, "확정/공유" 시점에 Routes 호출하는 2단계 구조.
- **난이도:** 중간

### 체류시간 경직성 (카테고리 고정값)

- **현황:** 디즈니랜드(ACTIVITY)와 동네 벽화골목(ACTIVITY)이 둘 다 120분. 분 단위 시작/종료를 노출하면 신뢰도 붕괴.
- **개선안:** 장소별 체류시간 DB 저장(Google Places `regularOpeningHours` 참조) 또는 사용자 편집 가능 필드.
- **난이도:** 중간

---

## 미결정 UI 정책

| 질문 | 현재 상태 | 권장 방향 |
|---|---|---|
| Q1: 체류시간을 카드에 표시? | 미정 | "약 2시간" 어조, 정확 시간 비권장 |
| Q2: 이동시간을 카드 사이에 표시? | 미정 | "🚶 약 N분" 표시 권장, Routes 실측 선행 필요 |
| Q3: 분 단위 시작/종료 시간 노출? | 미정 | 비권장. "오전/점심/오후/저녁" 시간대 레이블 권장 |
| Q4: 사용자가 체류시간 수동 조정? | 미정 | 프론트 계산으로 처리 (알고리즘 재호출 불필요) |
| Q5: Density Point 수치 노출? | 미정 | 숫자 비노출. "여유/보통/빡빡" 3단계 추상 게이지 |
| Q6: 경고 배지 표시 방식? | 미정 | `LATE_SCHEDULE`/`DAY_OVERLOADED`는 day 배너, `MEAL_WINDOW_VIOLATION`/`OPENING_HOURS_UNVERIFIED`는 카드 아이콘 |

---

## 미결정 기능 질문

| 영역 | 질문 | 권장 방향 |
|---|---|---|
| A | 빈 Day를 UI에서 어떻게 표시? | "장소 추가 유도" 카드 + `DAY_EMPTY` 플래그 |
| A | 첫 일정 열람 시 온보딩 안내? | "예상 동선 가이드입니다" 1회 안내 |
| B | 한 멤버 편집 중 다른 멤버가 같은 day를 보면? | day 단위 편집 락 또는 "○○님이 편집 중" 표시 |
| B | REORDER vs RESTRUCTURE 자동 판별? | 순서변경 = 프론트 재계산, 추가/삭제 = PlanB/altPool 경로 |
| C | PlanB 후보 탭 시 즉시 교체 vs 확인 팝업? | 확인 팝업 또는 즉시교체 + undo |
| C | 교체 후 다른 멤버 화면 실시간 갱신? | 알림 + 자동 갱신(낙관적 업데이트) |
| D | `isOutlierCandidate=true` 장소 배치 시 UI 표시? | "이동 거리 김" 배지 + PlanB 유도 |
| E | RELAXED/PACKED를 그룹 생성 후 변경 가능? | "전체 일정 재생성" 동의 후 1회 재실행 |

---

## 잔여 우선순위

| 순위 | 항목 | 난이도 |
|---|---|---|
| 1 | 빈 Day 처리 / 시간 표시 정책 결정 (Q3·Q4·영역A) | 낮음(정책) |
| 2 | 동시 편집 락 / 실시간 갱신 (영역 B·C) | 중간 |
| 3 | Step2 FOOD 쿼터 effectiveK 연동 + load 리밸런싱 수렴 루프 | 중간 |
| 4 | rebalanceDensity remove를 placeId 기준으로 명시 | 낮음 |
