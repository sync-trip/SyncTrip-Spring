package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.auth.KakaoLoginRequest;
import com.sync.dto.auth.LoginResponse;
import com.sync.dto.auth.LogoutRequest;
import com.sync.dto.auth.TokenRefreshRequest;
import com.sync.dto.kakao.KakaoTokenResponse;
import com.sync.service.AuthService;
import com.sync.service.KakaoAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/kakao")
public class KakaoAuthController {

    private final KakaoAuthService kakaoAuthService;
    private final AuthService authService;
    private static final Logger log = LoggerFactory.getLogger(KakaoAuthController.class);

    public KakaoAuthController(KakaoAuthService kakaoAuthService, AuthService authService) {
        this.kakaoAuthService = kakaoAuthService;
        this.authService = authService;
    }

    @GetMapping("/callback")
    // 인가 코드(code)를 카카오 토큰으로 교환할 때 사용하는 콜백 엔드포인트
    public KakaoTokenResponse callback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state
    ) {
        return kakaoAuthService.exchangeToken(code, state);
    }

    @PostMapping("/login")
    // Android가 전달한 카카오 access token으로 사용자 조회 후 우리 서비스 JWT를 발급
    public LoginResponse login(@RequestBody KakaoLoginRequest request) {
        // 디버그: 토큰 전체를 로그에 남기지 말고 길이/접두사 정보만 기록
        String token = request.accessToken();
        int len = token == null ? 0 : token.length();
        String prefix = token == null ? "" : token.substring(0, Math.min(6, token.length()));
        log.info("Received login request: accessToken length={}, prefix={}", len, prefix);
        try {
            return authService.loginWithKakaoAccessToken(token);
        } catch (Exception ex) {
            // 예외가 발생하면 스택트레이스를 남기고 다시 던져서 클라이언트에 적절히 전달되게 함
            log.error("/auth/kakao/login 처리 중 예외 발생", ex);
            throw ex;
        }
    }

    @PostMapping("/refresh")
    // refresh token을 검증해 access/refresh 토큰 쌍을 재발급
    public LoginResponse refresh(@RequestBody TokenRefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@LoginUser Long userId,
                                       @RequestBody(required = false) LogoutRequest request) {
        String refreshToken = request != null ? request.refreshToken() : null;
        authService.logout(refreshToken);
        log.info("사용자 로그아웃: userId={}", userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/withdraw")
    // 회원탈퇴: 사용자 계정을 논리 삭제 (is_deleted = true)
    public ResponseEntity<Void> withdraw(@LoginUser Long userId) {
        authService.withdrawUser(userId);
        log.info("사용자 탈퇴 처리 완료: userId={}", userId);
        return ResponseEntity.ok().build();
    }
}

