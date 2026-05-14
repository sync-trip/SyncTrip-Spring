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
- 현재 작업: Google Places API 연동 + 테스트
- 예정: 서비스 레이어 통합 + 시나리오 테스트