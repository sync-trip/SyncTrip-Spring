# JWT 인증 연동 & 로그아웃/회원탈퇴 API 구현 가이드

**작성일**: 2026-05-12  
**상태**: ✅ 완료 (테스트 통과)

---

## 📌 목차
1. [개요](#개요)
2. [구현 내용](#구현-내용)
3. [파일 변경 사항](#파일-변경-사항)
4. [API 명세](#api-명세)
5. [인증 플로우](#인증-플로우)
6. [사용 예제](#사용-예제)
7. [보안 설정](#보안-설정)
8. [주의사항](#주의사항)

---

## 개요

### 목표
- JWT 토큰 기반 Stateless 인증 구현
- `@LoginUser` 어노테이션으로 현재 로그인 사용자 ID 자동 주입
- 로그아웃/회원탈퇴 API 구현
- Spring Security 통합

### 주요 특징
- **Stateless**: 서버에서 세션 미저장
- **Bearer Token**: HTTP Authorization 헤더 사용
- **JWT 검증**: 서명 + 만료시간 확인
- **Soft Delete**: 회원탈퇴 시 물리 삭제 대신 논리 삭제

---

## 구현 내용

### 🆕 새로 추가된 기능

#### 1. JWT 인증 필터 (`JwtAuthenticationFilter`)
```
요청 흐름:
Authorization: Bearer {token}
         ↓
추출: "Bearer " 제거
         ↓
검증: JWT 서명 & 만료시간 확인
         ↓
추출: subject (userId) 읽기
         ↓
SecurityContext 설정: principal = userId
         ↓
다운스트림 필터/핸들러로 진행
```

**위치**: `com.sync.common.security.JwtAuthenticationFilter`

#### 2. @LoginUser 어노테이션
```java
// 사용 예
@PostMapping("/api/bands")
public ResponseEntity<BandResponse> createBand(
    @LoginUser Long userId,  // 자동 주입
    @RequestBody BandCreateRequest request
) { ... }
```

이 어노테이션은 다음을 수행:
- SecurityContext에서 인증 정보 추출
- principal (userId) → Long으로 변환 및 주입

**위치**: `com.sync.common.annotation.LoginUser`

#### 3. 매개변수 리졸버 (`LoginUserArgumentResolver`)
- Spring이 `@LoginUser` 어노테이션을 인식하고 처리하도록 등록
- `HandlerMethodArgumentResolver` 인터페이스 구현

**위치**: `com.sync.common.security.LoginUserArgumentResolver`

#### 4. Spring Security 설정 (`SecurityConfig`)
```
핵심:
✓ Stateless 세션 관리 (STATELESS)
✓ CSRF 비활성화 (JWT 사용)
✓ 공개 엔드포인트 정의
✓ JWT 필터 등록
✓ ArgumentResolver 등록
```

**위치**: `com.sync.config.SecurityConfig`

#### 5. 로그아웃/회원탈퇴 로직
- **로그아웃**: Stateless JWT이므로 백엔드에서 할 일 없음 (프론트에서 토큰 삭제)
- **회원탈퇴**: `users.is_deleted = true` 설정 (Soft Delete)

**위치**: `com.sync.service.AuthService`

---

## 파일 변경 사항

### 🆕 새 파일

| 파일 | 설명 |
|------|------|
| `com.sync.common.annotation.LoginUser.java` | JWT 사용자 ID 주입 어노테이션 |
| `com.sync.common.security.JwtAuthenticationFilter.java` | JWT 검증 필터 |
| `com.sync.common.security.LoginUserArgumentResolver.java` | @LoginUser 파라미터 처리 |
| `com.sync.config.SecurityConfig.java` | Spring Security 설정 |

### 📝 수정 파일

| 파일 | 변경사항 |
|------|---------|
| `User.java` | `withdraw()` 메서드 추가 |
| `AuthService.java` | `logout()`, `withdrawUser()` 메서드 추가 |
| `KakaoAuthController.java` | `POST /auth/kakao/logout`, `DELETE /auth/kakao/withdraw` API 추가 |
| `BandController.java` | `tempUserId` 제거 → `@LoginUser Long userId` 적용 |
| `build.gradle` | `spring-boot-starter-security` 의존성 추가 |

---

## API 명세

### 1. 로그인 (기존)
```http
POST /auth/kakao/login
Content-Type: application/json

{
  "accessToken": "{카카오_액세스_토큰}"
}
```

**응답** (200 OK):
```json
{
  "userId": 1,
  "email": "user@example.com",
  "name": "김철수",
  "profileImageUrl": "https://...",
  "isNewUser": false,
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "accessTokenExpiresIn": 900,
  "refreshTokenExpiresIn": 1209600
}
```

---

### 2. 로그아웃 (새로 추가) ⭐
```http
POST /auth/kakao/logout
Authorization: Bearer {accessToken}
```

**응답** (200 OK):
```
(본문 없음)
```

**동작**:
- Stateless JWT이므로 백엔드에서는 로그 기록만 함
- 프론트엔드에서 로컬 저장소의 토큰 삭제 필요

**향후 개선** (선택사항):
```
1. Refresh Token 블랙리스트 저장
   → 토큰 탈취 후 무효화 가능
   
2. 로그아웃 기록 저장
   → 세션 추적
```

---

### 3. 회원탈퇴 (새로 추가) ⭐
```http
DELETE /auth/kakao/withdraw
Authorization: Bearer {accessToken}
```

**응답** (200 OK):
```
(본문 없음)
```

**동작**:
1. 사용자 존재 여부 확인
2. `users.is_deleted = true` 설정 (Soft Delete)
3. 기존 데이터는 모두 보존

**데이터 보존**:
- 모든 그룹/일정/북마크/지출 데이터 유지
- 향후 재가입 시 `is_deleted = false` 설정 (복구 가능)

**향후 개선** (선택사항):
```
1. 카카오 서버에 unlink 요청
   → 연동 앱 권한 해제
   
2. 탈퇴 사유 저장
   → 서비스 개선 데이터
   
3. 데이터 완전 삭제 (30일 후)
   → GDPR 등 규정 준수
```

---

### 4. 토큰 갱신 (기존)
```http
POST /auth/kakao/refresh
Content-Type: application/json

{
  "refreshToken": "{우리_리프레시_토큰}"
}
```

**응답** (200 OK):
```json
{
  "userId": 1,
  "email": "user@example.com",
  "name": "김철수",
  "profileImageUrl": "https://...",
  "isNewUser": false,
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "accessTokenExpiresIn": 900,
  "refreshTokenExpiresIn": 1209600
}
```

---

## 인증 플로우

### 전체 흐름도
```
┌─────────────────────────────────────────────────────────────┐
│ 클라이언트 요청                                               │
│ Authorization: Bearer {accessToken}                         │
└─────────────┬───────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────┐
│ Spring Security Filter Chain                                │
│                                                              │
│ 1. JwtAuthenticationFilter                                  │
│    - Bearer 토큰 추출                                        │
│    - JWT 검증 (서명, 만료시간)                              │
│    - SecurityContext에 인증 정보 저장                        │
│                                                              │
│ 2. 나머지 Security Filters                                  │
│    - CORS, CSRF 등                                          │
└─────────────┬───────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────┐
│ 라우팅 (@LoginUser 파라미터 처리)                           │
│                                                              │
│ @PostMapping                                                │
│ public void action(                                         │
│     @LoginUser Long userId  ← LoginUserArgumentResolver    │
│ )                                                           │
│                                                              │
│ 동작:                                                        │
│ 1. @LoginUser 어노테이션 감지                               │
│ 2. SecurityContext 접근                                     │
│ 3. principal (userId) 추출                                  │
│ 4. Long으로 변환 & 주입                                     │
└─────────────┬───────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────┐
│ 컨트롤러 메서드 실행 (userId 사용 가능)                     │
└─────────────────────────────────────────────────────────────┘
```

### JWT 토큰 구조
```
Header.Payload.Signature

Header: {
  "alg": "HS256",
  "typ": "JWT"
}

Payload: {
  "sub": "1",           // userId (subject)
  "iss": "synctrip",    // issuer
  "type": "access",     // access 또는 refresh
  "provider": "KAKAO",  // OAuth 제공자
  "iat": 1715500800,    // 발급 시간
  "exp": 1715501700     // 만료 시간 (15분 후)
}

Signature: HMAC-SHA256(header + payload, secret)
```

---

## 사용 예제

### 방식 1: @LoginUser 어노테이션 (권장) ⭐
```java
@RestController
@RequestMapping("/api/bands")
public class BandController {
    
    @PostMapping
    public ResponseEntity<BandResponse> createBand(
        @LoginUser Long userId,  // ← 자동 주입
        @RequestBody BandCreateRequest request
    ) {
        return ResponseEntity.ok(bandService.createBand(userId, request));
    }
    
    @PostMapping("/join")
    public ResponseEntity<Void> joinBand(
        @LoginUser Long userId,
        @RequestBody BandJoinRequest request
    ) {
        bandService.joinBand(userId, request.inviteCode());
        return ResponseEntity.ok().build();
    }
}
```

### 방식 2: SecurityContextHolder 직접 사용
```java
@PostMapping
public ResponseEntity<BandResponse> createBand(
    @RequestBody BandCreateRequest request
) {
    // SecurityContext에서 직접 추출
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String userIdStr = (String) auth.getPrincipal();
    Long userId = Long.parseLong(userIdStr);
    
    return ResponseEntity.ok(bandService.createBand(userId, request));
}
```

### 안드로이드 클라이언트 구현
```kotlin
// 1. 로그인 성공
val loginResponse = apiService.login(kakaoAccessToken)

// 2. 토큰 저장
sharedPreferences.edit().apply {
    putString("access_token", loginResponse.accessToken)
    putString("refresh_token", loginResponse.refreshToken)
}.apply()

// 3. 이후 모든 API 요청에 헤더 추가
val request = originalRequest.newBuilder()
    .header("Authorization", "Bearer ${sharedPreferences.getString("access_token", "")}")
    .build()

// 4. 401 응답 시 토큰 갱신
if (response.code == 401) {
    val newTokens = apiService.refresh(refreshToken)
    // 새 accessToken으로 원래 요청 재시도
}

// 5. 로그아웃 (백엔드 호출 + 로컬 정리)
apiService.logout()
sharedPreferences.edit().apply {
    remove("access_token")
    remove("refresh_token")
}.apply()

// 6. 회원탈퇴
apiService.withdraw()
// 앱 초기 화면으로 이동
```

---

## 보안 설정

### ✅ 공개 엔드포인트 (인증 불필요)

```java
// SecurityConfig에서 정의
.requestMatchers(
    "/api/auth/kakao/login",      // 로그인
    "/api/auth/kakao/callback",    // OAuth 콜백
    "/swagger-ui/**",              // Swagger UI
    "/v3/api-docs/**",             // OpenAPI 문서
    "/actuator/**"                 // 헬스 체크
).permitAll()
```

**이유**:
- 로그인 전 사용자는 인증 불가능
- 개발/모니터링 목적의 엔드포인트

### 🔒 보호 엔드포인트 (JWT 필수)

```java
// 나머지 모든 /api/** 엔드포인트
.anyRequest().authenticated()
```

**포함되는 API**:
- 모든 밴드 관리 (`/api/bands/**`)
- 장소 북마크 (`/api/places/bookmarks/**`)
- 일정 관리 (`/api/schedules/**`)
- 지출 관리 (`/api/expenses/**`)
- 로그아웃/탈퇴 (`/auth/kakao/logout`, `/auth/kakao/withdraw`)

### 토큰 검증 절차

```
Bearer Token 검증 순서:
1. 형식 확인: "Bearer " prefix
2. 추출: "Bearer " 제거
3. JWT 파싱: Header.Payload.Signature 분리
4. 서명 검증: HMAC-SHA256 재계산
5. 만료시간 확인: exp claim vs 현재시간
6. subject 추출: userId 읽기

실패 시: 401 UNAUTHORIZED
```

---

## 주의사항

### ⚠️ 현재 상태의 한계

#### 1. Refresh Token 블랙리스트 미구현
```
현재: refresh 토큰 유효성 검사 없음
문제: 탈퇴/로그아웃 후에도 토큰으로 access 토큰 재발급 가능

해결책 (선택사항):
- Redis에 블랙리스트 저장
- 탈퇴/로그아웃 시 refresh 토큰 추가
- refresh 호출 시 블랙리스트 확인
```

#### 2. Token Rotation 미구현
```
현재: refresh 토큰이 계속 유효함
권장: refresh 토큰도 갱신
- 매 갱신 시 새 refresh 토큰 발급
- 이전 refresh 토큰 무효화
```

#### 3. 카카오 Unlink 미구현
```
현재: 우리 DB에만 탈퇴 처리
선택: 카카오 서버에도 권한 해제
- POST /v1/user/unlink
- 카카오 계정과 앱 연동 해제
```

### ✅ 현재 코드의 강점

```
✓ Stateless 구조: 확장성 우수
✓ Bearer Token 표준: 다른 서버와 호환
✓ Soft Delete: 데이터 보존
✓ 명확한 인증 플로우: 유지보수 용이
✓ 테스트 통과: 안정성 확보
```

---

## 문제 해결

### Q1: 401 UNAUTHORIZED 응답이 나옵니다
```
확인사항:
1. Authorization 헤더 포함? Bearer {token} 형식?
2. 토큰 만료? 만료되면 갱신 필요
3. 토큰이 access 토큰? (refresh 토큰 아님)
4. 서명 유효? JWT_SECRET과 발급 때 사용한 secret 동일?
```

### Q2: @LoginUser에 userId가 주입되지 않습니다
```
확인사항:
1. SecurityConfig에 ArgumentResolver 등록? (@Bean 필수)
2. JwtAuthenticationFilter가 실행됨? (SecurityFilterChain에 등록)
3. JWT 토큰 유효? (검증 통과)
4. 토큰 subject(sub claim)에 userId 포함? (JwtTokenProvider.createToken 확인)
```

### Q3: CORS 오류가 발생합니다
```
해결책 (application.yml에 추가):
cors:
  allowed-origins: http://localhost:3000,https://app.sync-trip.com
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allowed-headers: *
  allow-credentials: true

OR 별도 CorsConfig 클래스 작성
```

---

## 다음 단계 (로드맵)

### Phase 1: 현재 (완료) ✅
- [x] JWT 인증 필터 구현
- [x] @LoginUser 어노테이션
- [x] 로그아웃 API
- [x] 회원탈퇴 API
- [x] Spring Security 통합

### Phase 2: 보안 강화 (선택)
- [ ] Refresh Token 블랙리스트
- [ ] Token Rotation
- [ ] 카카오 Unlink 연동
- [ ] CSRF 토큰 (필요 시)

### Phase 3: 권한 관리 (향후)
- [ ] Role 기반 접근 제어 (RBAC)
  - OWNER: 그룹/일정 생성/삭제
  - MEMBER: 북마크/투표만
  - ADMIN: 전체 관리
- [ ] 세부 권한 검증

---

## 참고 자료

- JWT 표준: https://tools.ietf.org/html/rfc7519
- Spring Security 공식 문서: https://spring.io/projects/spring-security
- OWASP 인증 가이드: https://cheatsheetseries.owasp.org/

---

**마지막 업데이트**: 2026-05-12  
**상태**: ✅ 프로덕션 준비 완료 (기본 기능)

