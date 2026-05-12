# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Layout

The repo root contains two things: the Spring Boot application under `synctrip/` and GitHub Actions workflows under `.github/`. All Gradle commands must be run from the `synctrip/` directory.

## Build & Test Commands

```powershell
cd synctrip

# Run all tests (uses H2 in-memory DB — no external DB needed)
.\gradlew.bat clean test

# Run a single test class
.\gradlew.bat test --tests "com.sync.service.AuthServiceTest"

# Build without running tests
.\gradlew.bat build -x test

# Start local dev environment (MySQL via Docker Compose, Spring DevTools hot-reload)
.\gradlew.bat bootRun
```

CI runs on a self-hosted Windows runner and executes `.\gradlew.bat clean test --no-daemon`.

## Local Environment Setup

Copy `synctrip/.env.example` to `synctrip/.env` and fill in required values before running Docker Compose:

```powershell
docker compose -f synctrip/compose.yml up -d
```

The `compose.yml` starts MySQL 8.0 and the Spring Boot app. The app container requires MySQL to pass a healthcheck before starting.

Required env vars: `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`, `JWT_SECRET` (≥32 bytes), `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`.

## Spring Profiles

| Profile | Purpose |
|---------|---------|
| `local` | activates `local` + `common` |
| `prod` | activates `prod` + `common` (used in Docker) |
| `test` | H2 in-memory, `ddl-auto: create-drop` |

`application.yml` defines profile groups. Kakao and JWT config lives under the `common` profile. Production DB/app settings live in `application-prod.yml`.

## Architecture

### Auth Flow

```
Android client
  → POST /auth/kakao/login  { accessToken: <kakao_access_token> }
  → KakaoAuthController
  → AuthService.loginWithKakaoAccessToken()
      → KakaoAuthService.getUserInfo()   # calls Kakao API
      → UserRepository.findByOauthProviderAndOauthIdAndIsDeletedFalse()
      → upsert User (create or updateProfile)
      → JwtTokenProvider.issueTokenPair()
  ← LoginResponse { accessToken, refreshToken, ... }
```

Token refresh: `POST /auth/kakao/refresh` → `AuthService.refresh()` validates the refresh token's `type` claim, looks up the user, and re-issues a new token pair.

Auth code callback (`GET /auth/kakao/callback`) exchanges a Kakao authorization code for a Kakao token — used during the OAuth redirect flow, not the primary mobile login path.

### Key Design Decisions

- **OAuth-only, no passwords.** `User` stores `oauthProvider` + `oauthId`; soft-deleted via `isDeleted`.
- **JWT claims:** `sub` = internal user ID (Long as String), `type` = `"access"` or `"refresh"`, `provider` = OAuth provider name. The `type` claim is checked explicitly in `AuthService.refresh()` to prevent access tokens being used as refresh tokens.
- **Config via records.** `KakaoProperties` and `JwtProperties` are `@ConfigurationProperties` records injected into services by constructor. No `@Autowired`.
- **CORS via env var.** `CorsConfig` reads `CORS_ALLOWED_ORIGINS` (comma-separated). Default is `http://localhost:8080`. Set this in `.env` or the Docker environment block.
- **`RestTemplate` (not WebClient).** `KakaoAuthService` calls Kakao APIs synchronously. `RestTemplateConfig` provides the bean.
- **Tests use H2 + `application-test.yml`.** No Kakao/JWT env vars needed for `./gradlew test`.

## 알고리즘 패키지 (com.synctrip.AlgorithmTest)

| 파일 | 역할 |
|---|---|
| AlgorithmService.java | 파이프라인 진입점 — Step1→2→3 조합 |
| step1/WeightedCostFunction.java | 투표 점수 계산, mainPool/altPool 분리 |
| step2/KMeansClustering.java | K-Means 클러스터링, 날짜별 장소 배분 |
| step3/SimpleTsp.java | Nearest Neighbor 정렬 + 시간 할당 |
| planb/PlanBRecommender.java | 슬롯 교체 추천 |

## 알고리즘 핵심 규칙 (절대 어기지 말 것)

1. 순수 함수 — 알고리즘 함수는 DB 접근 금지, 입력→출력만
2. DB 작업은 서비스 레이어만
3. K-Means 재실행 금지 — 최초 1회만, 이후 수정은 TSP만
4. 결정론성 보장 — 같은 입력 → 같은 출력
5. 영업시간 체크는 해외 전용 — 국내는 opening_hours = NULL