package com.sync.controller;

import com.sync.dto.auth.KakaoLoginRequest;
import com.sync.dto.auth.LoginResponse;
import com.sync.dto.auth.TokenRefreshRequest;
import com.sync.dto.kakao.KakaoTokenResponse;
import com.sync.service.AuthService;
import com.sync.service.KakaoAuthService;
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
        return authService.loginWithKakaoAccessToken(request.accessToken());
    }

    @PostMapping("/refresh")
    // refresh token을 검증해 access/refresh 토큰 쌍을 재발급
    public LoginResponse refresh(@RequestBody TokenRefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
}
