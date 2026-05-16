package com.sync.controller;

import com.sync.domain.user.OauthProvider;
import com.sync.domain.user.User;
import com.sync.repository.UserRepository;
import com.sync.service.jwt.JwtTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 환경 전용 디버그 엔드포인트
 *
 * 활성화 조건: app.security.enabled=false (개발 모드)
 *
 * 용도:
 * - 토큰 없이 API 테스트
 * - 테스트 사용자 생성
 * - 테스트용 JWT 토큰 발급
 *
 * 운영 환경에서는 이 클래스가 로드되지 않음
 */
@RestController
@RequestMapping("/debug")
@ConditionalOnProperty(
    name = "app.security.enabled",
    havingValue = "false",
    matchIfMissing = true  // 기본값: 개발 모드
)
public class DebugController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public DebugController(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    /**
     * 상태 확인: 개발 모드인지 확인
     *
     * curl http://localhost:8080/debug/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(new StatusResponse(
            true,
            "개발 모드 활성화됨, 모든 API가 인증 없이 접근 가능합니다"
        ));
    }

    /**
     * 테스트 사용자 생성 또는 조회
     *
     * curl -X POST http://localhost:8080/debug/user?name=testuser
     */
    @PostMapping("/user")
    public ResponseEntity<?> createOrGetTestUser(
        @RequestParam(defaultValue = "테스트사용자") String name,
        @RequestParam(defaultValue = "test@example.com") String email,
        @RequestParam(defaultValue = "testuser_123") String oauthId
    ) {
        // 기존 사용자 조회
        var user = userRepository.findByOauthProviderAndOauthIdAndIsDeletedFalse(
            OauthProvider.KAKAO, oauthId
        );

        if (user.isPresent()) {
            return ResponseEntity.ok(new UserResponse(
                user.get().getId(),
                user.get().getName(),
                user.get().getEmail(),
                "(기존 사용자)"
            ));
        }

        // 새 사용자 생성
        User newUser = User.kakaoUser(email, name, null, oauthId);
        userRepository.save(newUser);

        return ResponseEntity.created(null).body(new UserResponse(
            newUser.getId(),
            newUser.getName(),
            newUser.getEmail(),
            "(신규 생성)"
        ));
    }

    /**
     * 테스트용 JWT 토큰 발급
     *
     * curl -X POST http://localhost:8080/debug/token?userId=1
     */
    @PostMapping("/token")
    public ResponseEntity<?> getTestToken(
        @RequestParam(defaultValue = "1") Long userId
    ) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: userId=" + userId));

        JwtTokenProvider.TokenPair tokenPair = jwtTokenProvider.issueTokenPair(user);

        return ResponseEntity.ok(new TokenResponse(
            user.getId(),
            user.getName(),
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            tokenPair.accessTokenExpiresIn(),
            tokenPair.refreshTokenExpiresIn(),
            "사용: Authorization: Bearer {accessToken}"
        ));
    }

    // ============ DTO ============

    record StatusResponse(
        boolean success,
        String message
    ) {}

    record UserResponse(
        Long userId,
        String name,
        String email,
        String note
    ) {}

    record TokenResponse(
        Long userId,
        String name,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn,
        String usage
    ) {}
}

