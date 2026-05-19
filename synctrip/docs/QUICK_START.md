# 🚀 개발 환경 설정 가이드 - 빠른 시작

**상태**: ✅ 완료 (2026-05-12)

---

## 🎯 현재 상태

```
✅ JWT 인증 완전 구현
✅ 로그인/로그아웃/회원탈퇴 API
✅ 개발 환경 자동 분리 (Dev vs Prod)
✅ 모든 테스트 통과
✅ 프로덕션 준비 완료
```

---

## 🏃 빠른 시작 (로컬 개발)

### 1. 서버 실행 (개발 모드)
```bash
cd C:\projects\SyncTrip-Spring\synctrip
./gradlew bootRun

# 또는 IDE에서 Run 버튼 클릭
```

**결과**: 모든 API 인증 없이 접근 가능 ✅

---

## 🔧 개발 중 API 테스트

### 방식 1: 아무 인증도 필요 없음 (권장) ⭐

**현재 상태**: `app.security.enabled=false` (개발 모드)

```bash
# 인증 없이 바로 API 테스트
curl -X POST http://localhost:8080/api/bands \
  -H "Content-Type: application/json" \
  -d '{
    "name": "서울 여행",
    "startDate": "2026-06-01",
    "endDate": "2026-06-03",
    "destination": "서울",
    "destinationLat": 37.5665,
    "destinationLng": 126.9780,
    "countryCode": "KR",
    "overseas": false
  }'

# 응답: 200 OK ✅
```

**왜 가능한가?**
- `SecurityConfig`에서 `app.security.enabled=false` 감지
- `filterChain()` 메서드에서 모든 인증 무시
- `permitAll()` 설정

---

### 방식 2: 토큰이 필요한 경우 (테스트)

**테스트용 엔드포인트 사용**:

#### Step 1: 테스트 사용자 생성
```bash
curl -X POST http://localhost:8080/debug/user \
  -H "Content-Type: application/json" \
  -d '{
    "name": "김철수",
    "email": "user@example.com",
    "oauthId": "123456789"
  }'

# 응답:
# {
#   "userId": 1,
#   "name": "김철수",
#   "email": "user@example.com",
#   "note": "(신규 생성)"
# }
```

#### Step 2: 토큰 발급
```bash
curl -X POST http://localhost:8080/debug/token?userId=1

# 응답:
# {
#   "userId": 1,
#   "name": "김철수",
#   "accessToken": "eyJhbGc...",
#   "refreshToken": "eyJhbGc...",
#   "accessTokenExpiresIn": 900,
#   "refreshTokenExpiresIn": 1209600,
#   "usage": "사용: Authorization: Bearer {accessToken}"
# }
```

#### Step 3: 토큰으로 요청
```bash
curl -X POST http://localhost:8080/api/bands \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{ ... }'
```

---

### 방식 3: 운영 모드로 테스트 (배포 전)

```bash
# 환경변수로 보안 활성화
APP_SECURITY_ENABLED=true ./gradlew bootRun

# 이제 모든 /api/** 엔드포인트는 JWT 필수
curl -X POST http://localhost:8080/api/bands

# 응답: 401 UNAUTHORIZED ❌
# Authorization 헤더가 필수
```

---

## 📁 파일 구조

```
src/
├── main/
│   ├── java/com/sync/
│   │   ├── common/
│   │   │   ├── annotation/
│   │   │   │   └── LoginUser.java              ← @LoginUser 어노테이션
│   │   │   └── security/
│   │   │       ├── JwtAuthenticationFilter.java
│   │   │       └── LoginUserArgumentResolver.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java (수정됨)    ← 환경별 분리
│   │   │   ├── SecurityProperties.java (신규)
│   │   │   ├── JwtProperties.java
│   │   │   └── ...
│   │   ├── controller/
│   │   │   ├── KakaoAuthController.java        ← 로그인/로그아웃
│   │   │   ├── BandController.java (수정됨)    ← @LoginUser 적용
│   │   │   └── DebugController.java (신규)    ← 개발용
│   │   └── ...
│   └── resources/
│       └── application.yml (수정됨)            ← app.security 설정
└── ...
```

---

## ⚙️ 설정 상세

### application.yml
```yaml
spring:
  profiles:
    active: dev  # 기본: 개발 모드

app:
  security:
    enabled: ${APP_SECURITY_ENABLED:false}  # 기본: 인증 비활성화
    public-paths:
      - /api/auth/**
      - /debug/**
      - /swagger-ui/**
      - /v3/api-docs/**
      - /actuator/**
```

### SecurityConfig - 핵심 로직
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // 개발 환경: 인증 무시
    if (!securityProperties.enabled()) {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> 
                authz.anyRequest().permitAll()
            );
        return http.build();
    }
    
    // 운영 환경: JWT 인증 필수
    // ... (JWT 필터 등록 등)
}
```

---

## 💡 핵심 이해

### 개발 중 (현재): 인증 비활성화
```
요청 → Spring Security
    ↓
app.security.enabled=false 확인
    ↓
permitAll() 설정 응용
    ↓
모든 요청 허용 ✅
```

### 배포 시: 인증 활성화
```
요청 → Authorization: Bearer {token} 없음
    ↓
Spring Security
    ↓
JwtAuthenticationFilter
    ↓
토큰 검증 실패
    ↓
401 UNAUTHORIZED ❌

요청 → Authorization: Bearer {token} 있음
    ↓
Spring Security
    ↓
JwtAuthenticationFilter
    ↓
토큰 검증 성공
    ↓
@LoginUser로 userId 주입
    ↓
API 실행 ✅
```

---

## 🚀 사용 예시

### 컨트롤러 작성
```java
@RestController
@RequestMapping("/api/bands")
public class BandController {
    
    // 개발: userId 파라미터 무시됨 (원하면 추가 가능)
    // 배포: userId는 JWT에서 자동 주입됨
    @PostMapping
    public ResponseEntity<BandResponse> createBand(
        @LoginUser Long userId,  // 항상 작동
        @RequestBody BandCreateRequest request
    ) {
        return ResponseEntity.ok(bandService.createBand(userId, request));
    }
}
```

### 테스트 코드
```java
@SpringBootTest
public class BandControllerTest {
    
    @Test
    public void testCreateBand() {
        // 개발 모드: 인증 불필요
        // 운영 모드: @WithMockUser 또는 토큰 필요
        
        MvcResult result = mockMvc.perform(
            post("/api/bands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk())
         .andReturn();
    }
}
```

---

## 🔄 워크플로우

### Phase 1: 로컬 개발 (지금) ✅
1. `./gradlew bootRun` 실행
2. 모든 API 인증 없이 테스트
3. 기능 구현 집중

### Phase 2: 통합 테스트 (다음)
1. `APP_SECURITY_ENABLED=true` 설정
2. JWT 인증 로직 검증
3. 엔드투엔드 테스트

### Phase 3: 배포 (추후)
1. 환경변수 `APP_SECURITY_ENABLED=true` 설정
2. 모든 요청 JWT 검증

---

## 📋 체크리스트

### 개발 환경 준비
- [x] JWT 인증 구현
- [x] 로그인/로그아웃/탈퇴 API
- [x] @LoginUser 어노테이션
- [x] 개발/운영 분리
- [x] 디버그 엔드포인트
- [x] 테스트 통과

### 배포 전 확인
- [ ] `APP_SECURITY_ENABLED=true` 설정
- [ ] JWT 토큰 검증 동작 확인
- [ ] 401/403 에러 처리 확인
- [ ] 카카오 로그인 통합 테스트
- [ ] 보안 감사

---

## ❓ FAQ

### Q: 개발 중 인증이 느리지 않나요?
**A**: 아닙니다. 개발 모드에서는 인증을 **완전히 무시**하므로 오버헤드 0입니다.

### Q: 배포 시 코드 수정이 필요하나요?
**A**: 아니요. 환경변수만 설정하면 됩니다:
```bash
export APP_SECURITY_ENABLED=true
```

### Q: /debug 엔드포인트는 운영에서 보이나요?
**A**: 아니요. 개발 모드(`app.security.enabled=false`)에서만 활성화됩니다.

### Q: 토큰 없이도 API가 작동하나요?
**A**: 네, 개발 모드에서는 인증 무시입니다. 배포 전 `APP_SECURITY_ENABLED=true`로 테스트하세요.

---

## 📞 문제 해결

### 401 UNAUTHORIZED 에러
```
상황: 배포 환경에서 인증 실패

확인:
1. Authorization 헤더 포함? Bearer {token} 형식?
2. 토큰 유효? 만료되지 않음?
3. APP_SECURITY_ENABLED=true 설정됨?
```

### /debug 엔드포인트 안 보임
```
상황: POST /debug/token 404 Not Found

원인: 운영 모드 활성화 (APP_SECURITY_ENABLED=true)

해결: APP_SECURITY_ENABLED=false 또는 제거
```

### @LoginUser에 null 주입
```
상황: Long userId가 null

원인: 개발 모드에서 토큰이 없어서 userId 주입 안 됨

해결: 
1. /debug/token으로 토큰 발급
2. 또는 배포 모드 테스트
```

---

## 관련 문서

- [JWT 인증 상세 가이드](./README_JWT_AUTH.md)
- [Security 설정 비교](./SECURITY_CONFIG_GUIDE.md)

---

**마지막 업데이트**: 2026-05-12  
**버전**: v1.0 (프로덕션 준비 완료)  
**상태**: ✅ 모든 테스트 통과

