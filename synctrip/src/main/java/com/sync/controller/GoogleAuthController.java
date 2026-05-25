package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.auth.LoginResponse;
import com.sync.dto.auth.LogoutRequest;
import com.sync.dto.auth.TokenRefreshRequest;
import com.sync.dto.google.GoogleLoginRequest;
import com.sync.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Google OAuth 인증 컨트롤러
 *
 * 엔드포인트:
 * - POST /auth/google/login: Google ID Token으로 로그인
 * - POST /auth/google/logout: 로그아웃
 * - DELETE /auth/google/withdraw: 회원탈퇴
 */
@RestController
@RequestMapping("/auth/google")
public class GoogleAuthController {

    private final AuthService authService;
    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    public GoogleAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    /**
     * Google ID Token으로 로그인
     * 안드로이드 Google Sign-In SDK로부터 받은 ID Token을 전달받아 로그인 처리
     *
     * @param request Google ID Token을 포함한 요청
     * @return JWT 토큰 및 사용자 정보를 포함한 로그인 응답
     */
    public LoginResponse login(@RequestBody GoogleLoginRequest request) {
        String idToken = request.idToken();
        int len = idToken == null ? 0 : idToken.length();
        String prefix = idToken == null ? "" : idToken.substring(0, Math.min(6, idToken.length()));
        log.info("Received Google login request: idToken length={}, prefix={}", len, prefix);
        try {
            return authService.loginWithGoogleIdToken(idToken);
        } catch (Exception ex) {
            log.error("/auth/google/login 처리 중 예외 발생", ex);
            throw ex;
        }
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
    /**
     * 회원탈퇴: 사용자 계정을 논리 삭제 (is_deleted = true)
     *
     * @param userId 탈퇴할 사용자 ID
     * @return 빈 응답
     */
    public ResponseEntity<Void> withdraw(@LoginUser Long userId) {
        authService.withdrawUser(userId);
        log.info("사용자 탈퇴 처리 완료: userId={}", userId);
        return ResponseEntity.ok().build();
    }
}

