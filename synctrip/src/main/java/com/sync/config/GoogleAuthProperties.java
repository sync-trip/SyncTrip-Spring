package com.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OAuth 설정 (모바일 앱용)
 *
 * 안드로이드 앱에서 이미 Google Sign-In SDK로 ID Token을 받아오므로,
 * 백엔드는 받은 ID Token을 검증하기 위해 클라이언트 ID만 필요합니다.
 *
 * 사용 위치:
 * - GoogleAuthService: ID Token JWT 서명 검증 (GoogleIdTokenVerifier)
 */
@ConfigurationProperties(prefix = "google.client")
public record GoogleAuthProperties(
        // Google OAuth 클라이언트 ID (필수)
        // 구글 클라우드 콘솔에서 "웹 애플리케이션"으로 생성한 클라이언트 ID
        // 형식: ~.apps.googleusercontent.com
        // 용도: ID Token의 audience(aud) claim 검증
        String id
) {
}



