# SyncTrip 프로젝트 인수인계 문서 v6
**작성일:** 2026-04-30 | DDL v6 + 알고리즘 의사코드 v2.4 반영

---

## 1. 프로젝트 개요

- **앱명:** SyncTrip
- **목적:** 그룹 여행 의사결정 갈등 해소 + 투표 기반 AI **자동 일정 초안** 생성
- **타겟:** 가족, 친구, 연인 소규모 그룹 여행자
- **핵심 가치:** "함께 결정하고, 시스템이 초안을 만들고, 사용자가 최종 조정한다"
- **유형:** 대학교 졸업 프로젝트 (논문 포함)
- **팀 인원:** 2~3명
- **기간:** 1~2개월
- **사용자(Min) 담당:** 알고리즘 + 알고리즘 입출력 데이터
- **팀원 담당:** 가계부/정산/알림/앨범/여권스탬프/API 백엔드

---

## 2. 기술 스택

| 레이어 | 기술 |
|---|---|
| Android Client | Kotlin, Android Studio |
| Backend | Spring Boot (Java) |
| Database | MySQL 8.0.16+ (Docker 로컬 환경) |
| 실시간 통신 | WebSocket (STOMP) |
| 장소 검색 | 국내: 카카오맵 API / 해외: 구글 Places API |
| 길찾기 | **외부 앱 위임** (해외=구글맵 / 국내=선택) |
| 환율 | ExchangeRate-API |
| 공휴일 | Nager.Date API |
| 영수증 스캔 | Vision AI (GPT-4o or Gemini Vision, 추후 결정) |
| 소셜 로그인 | 카카오 / 구글 |
| 알림 | In-App 알림만 (FCM 미사용) |

> **이동 수단 정책:** 도보/대중교통만 지원. 자차/렌터카는 v1 범위 외.

---

## 3. 핵심 개발 철학

> **"알고리즘은 합리적 초안을 만들고, 사용자가 자유롭게 조정한다."**
> **"분 단위 정확성보다 동선 가이드로서의 합리성을 추구한다."**

### 3.1 행동 원칙

1. **빠르고 간단한 초안** — 완벽한 최종 일정을 목표로 하지 않음
2. **사용자 자율성 우선** — 알고리즘이 모든 미시 제약을 잡으려 하지 않음
3. **수동편집을 신뢰** — 사용자가 알아서 조정함을 전제로 단순화
4. **결정론성 보장** — 같은 입력에 같은 결과 (시연 안정성)
5. **다수의 의사 충실히 반영** — 극단 케이스는 시스템이 책임지지 않음

### 3.2 검증 시 자문 질문

> "이 엣지 케이스를 알고리즘이 잡지 못하면 사용자에게 어떻게 영향?"
> 답이 "사용자가 알아서 조정함" → 알고리즘에서 안 잡아도 됨

---

## 4. 확정된 주요 설계 결정사항

### 그룹 / 멤버

| 항목 | 결정값 |
|---|---|
| 그룹 최대 인원 | 8명 |
| 1인당 장소 담기 | 5개 |
| 여행 스타일 | RELAXED / PACKED |
| 멤버 가입 (PLANNING) | 자유 |
| 멤버 가입 (VOTING/GENERATING) | 차단 |
| 멤버 가입 (TRAVELLING/DONE) | **권한 제한 허용** (v2.4 신규) |

### 투표

| 항목 | 결정값 |
|---|---|
| Ready 정의 | 장바구니 1개 이상 + "취소 불가" 팝업 |
| Ready 해제 | **불가** |
| 자동 시작 | 전원 Ready 시 즉시 |
| 강제 시작 | 방장 + 1개 이상 + **미Ready 사용자 있으면 경고창** (v2.4 신규) |
| 미투표 처리 | 투표 표명자 분모 (페널티 없음) |
| 본인 자동 LIKE | `result=0`, 일반 LIKE와 동일 카운트 |
| 단계 분리 | PLANNING ↔ VOTING 동시 진행 X |
| 자동 종료 | 전원 모든 장소 투표 완료 OR 1시간 OR 방장 수동 마감 |
| 알고리즘 자동 실행 | 투표 종료 → GENERATING → TRAVELLING 자동 |

### 알고리즘 파라미터

| 항목 | 결정값 |
|---|---|
| 통과 기준 | LIKE ≥ CEIL(전체 멤버 × 0.5) |
| altPool 구제 | LIKE 수 [CEIL(N×0.5)−2, CEIL(N×0.5)−1] AND vote_score > 0 |
| altPool 역할 | 사용자 수동편집/Plan B 시 추천 풀 (자동 대체 X) |
| Density Point | ACTIVITY=3, CULTURE/NATURE=2, SHOPPING/ETC=1, FOOD=0(쿼터별도) |
| RELAXED | FOOD 1개/일 + Density 5점 |
| PACKED | FOOD 2개/일 + Density 8점 |
| 하루 시작 | 09:00 |
| 하루 종료 상한 | 폐지 (v2.2) — 22:00 이후 LATE_SCHEDULE 배지만 |
| 이동 속도 | 25 km/h (도심 평균) |
| 영업시간 자동 검증 | 폐지 (v2.2) |

### 숙소 (v2.4 신규)

| 항목 | 결정값 |
|---|---|
| 입력 권한 | **방장(OWNER)만** |
| 입력 시점 | 그룹 생성 시 또는 투표 시작 전까지 |
| 입력 방법 | USR-007 (지도 검색) 재활용 |
| 변경 가능 단계 | PLANNING / TRAVELLING / DONE |
| 변경 불가 단계 | VOTING / GENERATING |
| **여행 중 변경** | **✅ 지원** (current_date 이후 day TSP 재계산) |
| 다중 숙소 (Day별) | ❌ v1 미지원 (v2 확장) |
| 알고리즘 영향 | Step 1, 2 무관 / Step 3 출발점만 |

### 지도 표시 (v2.4 신규)

| 항목 | 결정값 |
|---|---|
| 마커 표시 | Day 탭 전환 (해당 day만) |
| 마커 디자인 | 번호 + 카테고리 아이콘 (단색) |
| 하단 시트 | 장소명 / 평점 / 썸네일 / [세부정보→][길찾기] |
| 시간 정보 | 미표시 (일정표에 있음) |
| 경로 라인 | **표시 X** (마커만) |
| 길찾기 (해외) | **구글맵 강제** |
| 길찾기 (국내) | **선택 다이얼로그** (카카오/구글) |
| 통합 길찾기 | 미지원 |
| altPool 마커 | 미표시 (Plan B 호출 시에만) |

### Plan B (v2.3 도입)

| 항목 | 결정값 |
|---|---|
| 트리거 | 슬롯 Long Press → 바텀시트 |
| 검색 기준 | 닫힌 장소 DB 위경도 |
| 검색 알고리즘 | 1km 2단계 폭포수 (같은 카테고리만) |
| 최대 후보 | 7개 |
| Fallback | [지도 검색] 명시 클릭 시만 외부 API |
| 교체 동작 | RESTRUCTURE 트랜잭션 재활용 |
| Pool Swap | 방출 장소 → altPool 복귀 |
| 그룹 동시 편집 | 락 + 타임아웃 5분 |

### 영업시간 (v2.4 정책 변경)

| 항목 | 결정값 |
|---|---|
| 국내 (is_overseas=FALSE) | **opening_hours = NULL** (카카오 API 미제공) |
| 해외 (is_overseas=TRUE) | 구글 Places Details에서 정규화 저장 |
| JSON 스키마 | `{"MON": [{"open":"09:00","close":"22:00"}], ...}` |
| 24시간 영업 | `{"open":"00:00","close":"24:00"}` |
| 자정 넘김 | `23:59`로 단순화 |
| 휴무일 | 빈 배열 `[]` |
| 국내 OPENING_HOURS_UNVERIFIED 배지 | 표시 안 함 |
| 해외 배지 | 데이터 NULL → 표시 / 영업 외 → OUTSIDE_OPENING_HOURS |

---

## 5. 알고리즘 설계 현황 (의사코드 v2.4 기준)

### ✅ 완료된 알고리즘 영역

| 영역 | 상태 |
|---|---|
| Step 1 Weighted Cost Function | v2.1 검증 완료 |
| Step 2 K-Means Clustering | v2.2 검증 완료 (FIX-18~24) |
| Step 3 Simple Order & Time TSP | v2.2 단순화 + v2.4 current_date 추가 |
| REORDER 모드 (수동편집) | v2.0 완료 |
| Plan B (USR-018+031 통합) | v2.3 정식 도입 |
| 숙소 정책 + 호텔 변경 | v2.4 신규 |
| 지도 표시 정책 | v2.4 신규 |
| 투표/Ready 정책 | v2.4 명세 완료 |
| 영업시간 정책 (해외 전용) | v2.4 결정 |
| 알고리즘 입출력 명세 | v2.4 신규 |

### Step 1 핵심 공식

```
total_voters  = LIKE + DISLIKE  (미투표 제외)
like_rate     = LIKE / total_voters
dislike_rate  = DISLIKE / total_voters
vote_score    = (like_rate × 2) − dislike_rate
norm_dist     = max_dist < 3km ? 0 : (dist / max_dist)
priority_score = (vote_score × 0.7) − (norm_dist × 0.3)

passed_threshold = CEIL(total_members × 0.5)
passed   = like_count >= passed_threshold
altPool  = like_count IN [N/2-2, N/2-1] AND vote_score > 0
```

### Step 3 시그니처 (v2.4 변경)

```
step3_simple_tsp(
  clusters, K, accommodation, destination, travel_style,
  start_date,
  current_date = null,        // 호텔 변경 시 사용
  existing_schedules = null   // 과거 day 보존용
)
```

### 알고리즘 입출력 명세 (v2.4 신규)

5개 함수 모두 **순수 함수**로 정의:
- `step1_weighted_cost`
- `step2_kmeans`
- `step3_simple_tsp`
- `restructure_day`
- `plan_b_recommend`

DB 조회/저장은 서비스 레이어 책임. 의사코드 §11 참조.

### 경고 배지

| 코드 | 의미 |
|---|---|
| `TIME_OUT_OF_MEAL_WINDOW` | FOOD가 식사 Window 밖 |
| `OUTSIDE_OPENING_HOURS` | 영업시간 외 (해외 + 데이터 있음) |
| `OPENING_HOURS_UNVERIFIED` | 영업시간 정보 없음 (해외만 표시) |
| `LATE_SCHEDULE` | 22:00 이후 시작 |
| `OUTLIER_FULL_DAY` | 이상치 장소가 하루 통째 차지 |

---

## 6. 검토 후 미채택 사항

| 검토안 | 결과 | 이유 |
|---|---|---|
| 여행 스케일 차등화 (4단계 자동) | ❌ 폐기 | 자동 추측 = 사용자 모름 = 철학 위배 |
| 자차 SPEED 분기 | ❌ 폐기 | 도보/대중교통만 지원 정책 |
| Ollama 메인 일정 생성 | ❌ 폐기 | 시연 리스크 / 비결정론 |
| Plan B 외부 API 자동 호출 | ❌ 폐기 | 비용 + 사용자 자율 |
| Plan B 5km/15km 3단계 | ❌ 폐기 | 5km 너무 멀음 (도보 1시간) → 1km 2단계 |
| USR-018, 031 분리 | ❌ 폐기 | 시점만 다름, 단일 로직 통합 |
| 다중 숙소 (Day별) v1 | ❌ 폐기 | DDL 부담 + 빈도 낮음, v2 확장 |
| 길찾기 API 인앱 표시 | ❌ 폐기 | 비용 + 사용자 외부 앱 친숙 |
| 지도 경로 라인 직선 표시 | ❌ 폐기 | 강 가로지름 어색 |
| Step 3 영업시간 자동 검증 | ❌ 폐기 (v2.2) | 데이터 불완전 + 사용자 자율 |
| Step 3 21:00 상한 | ❌ 폐기 (v2.2) | Density가 일정량 제어 |
| Step 3 자유시간 자동 슬롯 | ❌ 폐기 (v2.2) | UI에서 자동 표시 |
| Step 2 mainPool 보충 | ❌ 폐기 (v2.2) | 사용자 수동편집으로 보충 |
| Ollama 보조 설명 | ⚠️ 보류 | v2 확장 여지 |

---

## 7. 요구사항 정의서 (USR-001 ~ USR-031)

| ID | 분류 | 기능명 | 변경 |
|---|---|---|---|
| USR-001 | 회원 관리 | 회원가입/로그인 (소셜) | — |
| USR-002 | 회원 관리 | 정보 수정 / 탈퇴 | — |
| USR-003 | 그룹 관리 | 그룹 생성 (+ 숙소 입력) | v2.4 명세 |
| USR-004 | 그룹 관리 | 그룹 초대 / 참여 | — |
| USR-005 | 그룹 관리 | 최대 인원 제한 | — |
| USR-006 | 그룹 관리 | 초대 코드 재발급 | — |
| USR-007 | 장소 탐색 | 지도 장소 검색 | 숙소 입력에도 재활용 |
| USR-008 | 장소 탐색 | 블라인드 장바구니 담기 | — |
| USR-009 | 장소 탐색 | Ready 상태 전환 | v2.4 명세 (취소 불가) |
| USR-010 | 의사결정 | 스와이프 투표 | — |
| USR-011 | 의사결정 | 카테고리별 순위 풀 | — |
| USR-012 | 의사결정 | Density 기반 슬롯 편입 | — |
| USR-013 | 의사결정 | 최종 결과 확인 | — |
| USR-014 | 의사결정 | 투표 강제 시작/마감 | v2.4 경고창 추가 |
| USR-015 | 일정 관리 | 일자별 동선 초안 | — |
| USR-016 | 일정 관리 | 이상치 감지 / 제외 | — |
| USR-017 | 일정 관리 | Drag & Drop 순서 변경 | — |
| **USR-018** | 일정 관리 | 수동 편집 시 대안 팝업 | **Plan B 통합** (v2.3) |
| USR-019 | 가계부 | 영수증 OCR | — |
| USR-020 | 가계부 | 지출 수동 관리 | — |
| USR-021 | 가계부 | 다통화 환율 | — |
| USR-022 | 아카이빙 | 더치페이 정산 | — |
| USR-023 | 아카이빙 | 공유 앨범 | — |
| USR-024 | 아카이빙 | 여권 스탬프 | — |
| USR-025 | 아카이빙 | 과거 여행 기록 | — |
| USR-026 | 알림/설정 | 주요 이벤트 In-App 알림 | — |
| USR-027 | 알림/설정 | 알림 토글 | — |
| USR-028 | 알림/설정 | 여행 종료 처리 | — |
| USR-029 | 알림/설정 | 로그아웃 / 세션 만료 | — |
| USR-030 | 알림/설정 | 공휴일 알림 (Nager.Date) | — |
| **USR-031** | 일정 관리 | 실시간 플랜 B 추천 | **Plan B 통합** (v2.3) |

---

## 8. DB 설계 (DDL v6)

**총 17개 테이블 + 트리거 2개. v2.4 의사코드와 정합성 맞춤.**

| # | 테이블 | 용도 | v6 변경 |
|---|---|---|---|
| 1 | users | 회원 + 알림 설정 통합 | — |
| 2 | groups | 그룹 + travel_style + 숙소 | — |
| 3 | group_vote_info | 투표 시작/종료 + 강제시작 여부 | — |
| 4 | group_finance | 그룹 기준 통화 | — |
| 5 | group_exchange_rates | 다통화 환율 | — |
| 6 | **group_members** | 멤버 + Ready + bookmark_count | **+ `joined_after_voting`** ⭐ |
| 7 | **places** | 장소 + density_point + opening_hours | **opening_hours 코멘트 보강** |
| 8 | place_bookmarks | 장바구니 (트리거 동기화) | — |
| 9 | votes | 투표 (1/-1/0, UPDATE 금지) | — |
| 10 | schedules | 일정 + day_number | — |
| 11 | **schedule_alts** | altPool (Plan B 추천 풀) | **− `alt_rank` 제거** |
| 12 | expenses | 지출 | — |
| 13 | expense_members | 더치페이 분담자 | — |
| 14 | notifications | 알림 (type ENUM 4종) | — |
| 15 | album_photos | 공유 앨범 | — |
| 16 | passport_stamps | 여권 스탬프 | — |

### v5 → v6 변경사항 (의사코드 v2.4 정합성)

1. **`group_members.joined_after_voting` 컬럼 추가** [FIX-39]
   - TRAVELLING/DONE 단계 가입자의 권한 구분
   - TRUE = 장바구니 추가 / 투표 불가
   - FALSE = 정상 멤버

2. **`places.opening_hours` 코멘트 보강** [FIX-40]
   - 해외 전용 (국내는 카카오 API 미제공으로 NULL)
   - JSON 스키마 명시

3. **`schedule_alts.alt_rank` 제거** [FIX-31]
   - Plan B 폭포수 검색이 동적 정렬 사용 (priority_score + 거리)
   - 사용되지 않는 컬럼 제거

> v2.4 알고리즘 변경의 DDL 영향은 위 3건만. 호텔 변경, Plan B 트리거, 지도 표시는 모두 기존 컬럼 재활용.

**최신 DDL 파일:** `SyncTrip_DDL_v7.sql`

---

## 9. 알고리즘 패치 이력 요약

### v2.3 → v2.4 (숙소/지도/투표/입출력) — 7건
| ID | 변경 |
|---|---|
| FIX-35 | 숙소 정책 명세 (방장만, 여행 중 변경 가능) |
| FIX-36 | Step 3 시그니처 `current_date` 추가 |
| FIX-37 | 지도 표시 정책 (마커만, Day 탭) |
| FIX-38 | 길찾기 외부 앱 위임 |
| FIX-39 | 투표/Ready 정책 9건 |
| FIX-40 | 영업시간 해외 전용 |
| FIX-41 | 알고리즘 입출력 명세 |

### v2.2 → v2.3 (Plan B 도입) — 5건 (FIX-30~34)
### v2.1 → v2.2 (Step 2/3 검증 + 단순화) — 9건 + Step 3 단순화
### v2.0 → v2.1 (Step 1 검증) — 7건
### v1.0 → v2.0 — 10건

**최신 의사코드:** `SyncTrip_알고리즘의사코드_v2.4.md`

---

## 10. 다음 작업 순서

```
✅ DB 검토 (DDL v5)
✅ Step 1 알고리즘 + 검증 (v2.1)
✅ Step 2 알고리즘 + 검증 (v2.2)
✅ Step 3 알고리즘 + 단순화 (v2.2)
✅ REORDER 모드 (v2.0)
✅ Plan B 정식 명세 (v2.3)
✅ 숙소 정책 + 호텔 변경 (v2.4)
✅ 지도 표시 정책 (v2.4)
✅ 투표/Ready 정책 (v2.4)
✅ 영업시간 정책 (v2.4)
✅ 알고리즘 입출력 명세 (v2.4)

— Min(사용자) 알고리즘 작업 완료 —

⬜ 팀원 협업 영역
   - Spring Boot 프로젝트 세팅
   - DDL v5 적용
   - API 명세서 작성 (의사코드 §11 입출력 명세 활용)
   - 카카오/구글 카테고리 → SyncTrip Category 매핑 레이어
   - 가계부/정산/알림/앨범/여권 스탬프 구현
   - WebSocket 연동
   - Vision AI 선정 (영수증 OCR)
   - 안드로이드 클라이언트 개발

⬜ 알고리즘 구현 (Min)
   - 의사코드 v2.4 → Kotlin/Java 변환
   - 단위 테스트 작성
   - 통합 시나리오 검증
   - 백엔드 서비스 레이어와 연동
```

---

## 11. 결정 보류 / 확인 필요 사항 (팀원 협업 시)

### 알고리즘 영역 (Min 담당)

1. **카카오/구글 카테고리 → SyncTrip Category 매핑 룰**
   - 카카오: "음식점 > 한식 > 비빔밥" 등
   - 구글: "restaurant", "tourist_attraction" 등
   - SyncTrip enum: FOOD, CULTURE, ACTIVITY, SHOPPING, NATURE, ETC

2. **opening_hours 정규화 레이어** (해외 전용)
   - 구글 Places Details API → 의사코드 §12.2 JSON 스키마 변환
   - 자정 넘김 / 24시간 영업 / 휴무 처리

3. **Plan B 새 장소 INSERT**
   - 지도 검색에서 가져온 장소 → places.density_point/estimated_duration 자동 매핑
   - Category enum 매핑 후 INSERT

### 팀원 협업 영역

4. **Vision AI API 선정** (GPT-4o vs Gemini Vision)
5. **MySQL 8.0.16+ 확정**
6. **그룹 락 메커니즘** (DB? Redis? WebSocket 세션?)
7. **API 명세서 작성** (의사코드 §11 입출력 명세 활용)

---

## 12. 논문 어필 포인트 (v6 최종)

1. **Weighted Cost Function with Normalization**
   - 인원 정규화 + 미투표 제외 분모
   - 싫어요 테러 / 우물 안 개구리 방어

2. **Density Score 기반 슬롯 편입**
   - 카테고리 가중치, RELAXED/PACKED 2-모드

3. **Balanced K-Means with Adaptive Centroid**
   - 위도 보정 + 동적 radius + Tie-breaker로 결정론성

4. **Simple Order & Time TSP** ⭐
   - Step 1, 2가 합리적 후보 선별 → Step 3 단순화
   - 코드 60% 감소, 응답 속도 향상

5. **편집 유형별 차등 재계산** (REORDER / RESTRUCTURE)

6. **Plan B — 여행 전/중 통합 단일 로직**
   - 1km 2단계 폭포수 / DB 좌표 기준 / API 비용 0원

7. **여행 중 동적 호텔 변경 + 부분 재계산** ⭐ (v2.4)
   - current_date 이전 day 보존, 이후만 재계산
   - 시계열 도메인 자연스러운 설계

8. **순수 함수 알고리즘 + DB 분리 아키텍처** (v2.4)
   - 테스트 가능성, 결정론성 보장
   - 백엔드 / 알고리즘 병렬 개발 가능

9. **"분 단위 정확성보다 동선 가이드" 철학** ⭐
   - 미시 제약은 UI 경고 + 수동편집으로 위임
   - 단순화 / 결정론성 / 응답 속도 동시 달성

### 엔지니어링 판단력 어필 (면접용)

| 검토안 | 결정 | 인사이트 |
|---|---|---|
| Step 3 영업시간 자동 검증 | 폐지 | 데이터 불완전 + 사용자 자율 |
| altPool 자동 대체 | 폐지 | "분 단위 최적화 강박" 인식 |
| 여행 스케일 자동 차등화 | 미채택 | 자동 추측 = 사용자 모름 |
| Plan B 5km → 1km | 재검증 | 도보 1시간은 "근처" 아님 |
| USR-018, 031 분리 | 통합 | 단일 로직 우아함 |
| Plan B 외부 API 자동 호출 | 명시적 진입만 | 비용 + 사용자 자율 |
| 길찾기 인앱 표시 | 외부 앱 위임 | 비용 + 사용자 외부 앱 친숙 |
| 다중 숙소 v1 | v2 확장 | DDL 부담 vs 빈도 낮음 |
| 호텔 변경 시 전체 재계산 | 부분 재계산 | 시계열 보존 + 효율 |
| 영업시간 자체 | 해외 전용 | 데이터 가용성 정직한 인정 |

**"복잡도 vs 효과 trade-off"** 판단 사례 다수.

### Wanderlog 대비 차별점

- 그룹 투표 의사결정 (블라인드 + 스와이프)
- AI 자동 초안 일정 (K-Means + Simple TSP)
- 여행 전/중 통합 Plan B
- **여행 중 동적 호텔 변경**
- 사용자 자율 편집 (제약 없는 Drag & Drop, 경고만)

---

**문서 끝**
