This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
SyncTrip — 그룹 여행 의사결정 앱.
블라인드 장바구니 → 스와이프 투표 → AI 일정 자동 생성 (Step1 Weighted Cost → Step2 K-Means → Step3 TSP).

- 담당 파트: 알고리즘 + Google Maps API 연동
- 장소 검색: 국내 = 카카오맵 API (is_overseas=FALSE), 해외 = Google Places API (is_overseas=TRUE)

## Build & Test
cd synctrip
.\gradlew.bat clean test          # H2 in-memory, 외부 DB 불필요
.\gradlew.bat build -x test
.\gradlew.bat bootRun             # Docker Compose로 MySQL 먼저 실행 필요

CI: self-hosted Windows runner, .\gradlew.bat clean test --no-daemon

## Local Setup
synctrip/.env.example → synctrip/.env 복사 후 값 입력

docker compose -f synctrip/compose.yml up -d

Required env vars: KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET, KAKAO_REDIRECT_URI, JWT_SECRET (≥32 bytes), MYSQL_* 3종, GOOGLE_MAPS_API_KEY
Spring profiles: local (local+common) / prod (prod+common, Docker) / test (H2, create-drop)

## Architecture
- Auth flow: POST /auth/kakao/login { accessToken } → KakaoAuthService → upsert User → JWT 발급
- OAuth-only. sub = user ID, type = access|refresh (refresh 시 type 검증 필수)
- Config: @ConfigurationProperties records, constructor injection, no @Autowired
- RestTemplate (동기), CORS는 CORS_ALLOWED_ORIGINS env var

## 알고리즘 패키지 (com.synctrip.algorithm)
| 파일 | 역할 |
|---|---|
| AlgorithmService.java | Step1→2→3 파이프라인 진입점 |
| step1/WeightedCostFunction.java | 투표 점수, mainPool/altPool 분리 |
| step2/KMeansClustering.java | K-Means, 날짜별 장소 배분 |
| step3/SimpleTsp.java | Nearest Neighbor + 시간 할당 |
| planb/PlanBRecommender.java | 슬롯 교체 추천 |

## 절대 규칙 (CRITICAL)
1. 알고리즘 함수는 순수 함수 — DB 접근 금지, 입력→출력만
2. DB 작업은 서비스 레이어만 담당
3. K-Means 재실행 금지 — 최초 1회만. 이후 수정은 TSP만
4. 결정론성 보장 — 같은 입력 → 같은 출력
5. 영업시간 체크는 해외 전용 — 국내는 opening_hours = NULL
6. Google API FieldMask 필수 — X-Goog-FieldMask 항상 명시, * 와일드카드 금지
7. Google API 응답은 places 테이블에 캐싱 — external_id 기준 중복 저장 방지

## 현재 작업 단계
- 완료: 알고리즘 의사코드 구현 (Step1~3 + Plan B)
- 완료: 인증, 밴드, 투표, 일정, 가계부, 정산, 알림 (FCM 포함) 구현
- 미완료: 공유 앨범(USR-023), 여권 스탬프(USR-024), 공휴일 알림(USR-030)

## ★ 절대 규칙: 문서 업데이트 (모든 작업 시 필수, 예외 없음)

### 1) 구현 현황 문서 (`docs/SyncTrip_구현현황.md`)
코드를 추가·수정·삭제할 때마다 반드시 함께 업데이트한다.

- 인수인계 문서(v6)에 있는 기능 → ✅ 구현 / ⚠️ 부분 구현 / ❌ 미구현으로 상태 갱신
- 인수인계 문서에 없는 추가 기능 → ➕ 추가 구현으로 표시
- 각 기능에 구현일(YYYY-MM-DD) 기재
- 인수인계 문서와 다르게 결정된 사항은 12번 섹션에 추가
- 미구현 요약(11번 섹션)의 우선순위 갱신
- 변경 이력(하단 표)에 날짜와 변경 내용 한 줄 추가
- 문서 맨 마지막 줄의 "마지막 수정" 날짜 갱신
- 현재 사용 중인 DDL 파일명 정확히 명시

### 2) DDL 파일 (`synctrip/mysql/initdb.d/SyncTrip_DDL_vN.sql`)
스키마(테이블/컬럼/인덱스/제약조건)를 변경할 때마다 반드시 지킨다.

1. **파일 복사 후 버전 번호 +1** — 기존 파일은 절대 수정하지 않는다.
   예) `SyncTrip_DDL_v7.sql` → `SyncTrip_DDL_v8.sql`
2. **파일 상단 변경이력 주석 추가** — 최상단 주석 블록에 한국어로 작성.
   ```
   -- ════════════════════════════════════════
   -- v7 → v8 변경사항: YYYY-MM-DD
   --   1. 변경 항목 설명
   --   2. 변경 항목 설명
   -- ════════════════════════════════════════
   ```
3. **구현현황 문서의 DDL 파일명 갱신** — 문서 헤더와 맨 마지막 줄 모두 변경