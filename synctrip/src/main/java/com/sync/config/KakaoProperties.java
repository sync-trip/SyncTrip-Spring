package com.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kakao API 설정을 환경변수에서 주입받는 설정 클래스
 *
 * application.yml에서 kakao.* 로 시작하는 모든 설정을 자동으로 바인딩
 * 실제 값은 .env 파일에서 로드되고 Spring Boot가 자동 주입
 *
 * 사용 위치:
 * - KakaoAuthService: OAuth 로그인 (authorizationUri, tokenUri, userInfoUri, clientId 등)
 * - KakaoPlacesService: 국내 장소 검색 (localSearchUri, clientId)
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        // OAuth 2.0 인증 엔드포인트 (카카오 고정)
        String authorizationUri,
        // OAuth 토큰 발급 엔드포인트 (카카오 고정)
        String tokenUri,
        // 사용자 정보 조회 엔드포인트 (카카오 고정)
        String userInfoUri,
        // 국내 장소 검색 API 엔드포인트 (Kakao Local API category search)
        String localSearchUri,
        // Kakao 앱의 클라이언트 ID (필수, .env에서 주입)
        String clientId,
        // Kakao 앱의 클라이언트 Secret (필수, .env에서 주입)
        String clientSecret,
        // OAuth 콜백 URI (앱이 등록된 도메인과 일치해야 함)
        String redirectUri,
        // OAuth 스코프 (요청할 사용자 권한 범위)
        String scope
) {
}

