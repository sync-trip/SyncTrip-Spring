# Spring Security 설정 가이드

## ⚠️ 현재 문제점

```
현재 상태:
✓ 모든 /api/** 엔드포인트는 JWT 인증 필수
✓ 로그인 없이는 API 테스트 불가능
✓ 개발 중 매번 로그인 → 토큰 받기 → API 호출 필요

예시:
POST /api/bands (❌ 401 Unauthorized - 인증 없음)
```

## ✅ 해결책: 환경별 설정 분리

### 방법 1: Spring Profile 기반 (권장) ⭐

**application.yml** - 공통 설정
```yaml
spring:
  profiles:
    active: dev  # 개발(dev) 또는 운영(prod)
```

**application-dev.yml** - 개발 환경 (보안 느슨함)
```yaml
# 개발: 모든 엔드포인트 공개
app:
  security:
    enabled: false  # Security 비활성화
    public-paths:
      - /api/**      # 모든 API 공개
```

**application-prod.yml** - 운영 환경 (보안 엄격함)
```yaml
# 운영: 인증 필수
app:
  security:
    enabled: true   # Security 활성화
    public-paths:
      - /api/auth/**  # 인증 관련만 공개
```

**SecurityConfig.java 수정**:
```java
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityProperties securityProperties;
    
    public SecurityConfig(JwtTokenProvider jwtTokenProvider, 
                         SecurityProperties securityProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityProperties = securityProperties;
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 개발 환경에서 Security 비활성화
        if (!securityProperties.enabled()) {
            http.csrf(csrf -> csrf.disable());
            http.authorizeHttpRequests(authz -> 
                authz.anyRequest().permitAll()
            );
            return http.build();
        }
        
        // 운영 환경: 원래 설정 사용
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(securityProperties.publicPaths().toArray(new String[0]))
                .permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class
            );
        
        return http.build();
    }
    
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                if (securityProperties.enabled()) {
                    resolvers.add(new LoginUserArgumentResolver());
                }
            }
        };
    }
}
```

**SecurityProperties.java** 생성:
```java
package com.sync.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    boolean enabled,
    List<String> publicPaths
) {
}
```

---

### 방법 2: 테스트용 토큰 발급 엔드포인트 (간단함) ⭐⭐

**개발 환경에서만 활성화되는 엔드포인트 추가**:

```java
@RestController
@RequestMapping("/debug")
@Profile("dev")  // dev 프로필에서만 활성화
public class DebugController {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    
    public DebugController(JwtTokenProvider jwtTokenProvider, 
                          UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }
    
    /**
     * 개발용: 테스트 사용자로 토큰 발급
     * 운영에서는 이 엔드포인트가 존재하지 않음
     */
    @PostMapping("/token")
    public ResponseEntity<?> getDebugToken(
        @RequestParam(defaultValue = "1") Long userId
    ) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        JwtTokenProvider.TokenPair tokenPair = jwtTokenProvider.issueTokenPair(user);
        return ResponseEntity.ok(tokenPair);
    }
}
```

**사용법**:
```bash
# 개발 환경에서 테스트 토큰 받기
curl -X POST http://localhost:8080/debug/token?userId=1

# 응답:
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "accessTokenExpiresIn": 900,
  "refreshTokenExpiresIn": 1209600
}

# 이 토큰으로 API 호출
curl -X POST http://localhost:8080/api/bands \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{"name":"...","startDate":"...","endDate":"...",...}'
```

---

### 방법 3: 환경변수 + 조건부 필터 (고급)

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${app.jwt.enabled:true}")
    private boolean jwtEnabled;
    
    private final JwtTokenProvider jwtTokenProvider;
    
    @Override
    protected void doFilterInternal(...) {
        // 개발 환경에서 JWT 비활성화
        if (!jwtEnabled) {
            // 임시로 userId=1로 설정
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                    "1", null, Collections.emptyList()
                );
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
            return;
        }
        
        // 정상 JWT 처리 ...
    }
}
```

---

## 🎯 권장 설정 (개발 초기 단계)

### Step 1: application.yml 설정
```yaml
spring:
  profiles:
    active: dev

app:
  security:
    enabled: ${APP_SECURITY_ENABLED:false}  # 기본: 개발 모드
    public-paths:
      - /api/auth/**
      - /debug/**
      - /swagger-ui/**
      - /v3/api-docs/**
      - /actuator/**
```

### Step 2: 최소한의 코드 변경
1. `SecurityProperties` 클래스 추가
2. `SecurityConfig.java` 수정 (~10줄)
3. 끝!

### Step 3: 로컬 개발에서 사용
```bash
# 개발: 모든 API 공개
./gradlew bootRun

# 배포 전 테스트: 인증 필수
APP_SECURITY_ENABLED=true ./gradlew bootRun
```

---

## 💡 각 방법 비교

| 방법 | 장점 | 단점 | 추천 |
|------|------|------|------|
| **Profile 분리** | 환경별 명확한 분리 | 설정 파일 많음 | ✅ 프로덕션 준비 |
| **Debug Endpoint** | 간단하고 빠름 | 토큰 발급 필요 | ✅ 초기 개발 |
| **환경변수 조건** | 유연함 | 복잡함 | ⭕ 중급자 |

---

## 🚀 지금 바로 적용하는 방법 (추천)

### 1단계: application-dev.yml 생성
```yaml
# src/main/resources/application-dev.yml
app:
  security:
    enabled: false
```

### 2단계: application-prod.yml 생성
```yaml
# src/main/resources/application-prod.yml
app:
  security:
    enabled: true
    public-paths:
      - /api/auth/**
      - /swagger-ui/**
      - /v3/api-docs/**
      - /actuator/**
```

### 3단계: application.yml 수정
```yaml
spring:
  profiles:
    active: dev

app:
  security:
    enabled: false
    public-paths: []
```

### 4단계: SecurityConfig.java 수정
```java
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    
    private final SecurityProperties securityProperties;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 개발 환경: 모든 CORS/인증 비활성화
        if (!securityProperties.enabled()) {
            http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
            return http.build();
        }
        
        // 운영 환경: 원래 설정
        // ... (기존 코드)
    }
}
```

### 5단계: SecurityProperties.java 생성
```java
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    boolean enabled,
    List<String> publicPaths
) {
}
```

---

## ❓ FAQ

### Q: 개발 중에 Security 완전히 꺼도 되나요?
**A**: 네, 괜찮습니다. 로컬 개발 시에는:
- 보안 걱정 없음
- 빠른 개발 가능
- 배포 전에만 `enabled: true`로 테스트

### Q: 프로덕션에서는 반드시 켜야 하나요?
**A**: 네, 필수입니다:
- 무단 접근 차단
- 사용자 데이터 보호
- OWASP 보안 기준 준수

### Q: 운영 중에도 /debug 엔드포인트가 있으면 위험한가요?
**A**: 네, 위험합니다. `@Profile("dev")`로 개발 프로필에서만 활성화되므로 문제없습니다:
```java
@Profile("dev")  // 운영(prod) 프로필에서는 로드되지 않음
public class DebugController { ... }
```

### Q: 토큰 없이 API 테스트하고 싶으면?
**A**: 2가지 방법:
1. 스프링 부트 dev 프로필로 실행 (권장)
2. /debug/token 엔드포인트로 테스트 토큰 받기

---

## 📋 최종 요약

```
현재 상태: ✅ 프로덕션 코드 완성
문제: 개발 중 매번 인증 필요

해결책 3가지:
1. Profile 분리 (권장 - 프로덕션 준비)
2. Debug Endpoint (권장 - 초기 개발)
3. 환경변수 조건 (고급)

추천: 1번과 2번 조합
- 로컬: 인증 비활성화 (빠른 개발)
- 배포: 인증 활성화 (안전)
- 테스트 필요 시: /debug/token 사용
```

---

**다음 단계**: SecurityProperties/Profile 설정 추가 필요 시 알려주세요!

