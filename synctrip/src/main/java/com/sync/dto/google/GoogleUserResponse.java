package com.sync.dto.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Google OAuth 사용자 정보 응답 DTO
 * Google ID Token 검증 후 받은 사용자 정보
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleUserResponse(
        // Google 사용자 ID (고유 식별자)
        String sub,
        // 사용자 이름
        String name,
        // 사용자 이메일 (프로필 공개 설정시에만 제공)
        @JsonProperty("email")
                String email,
        // 프로필 사진 URL
        @JsonProperty("picture")
                String picture,
        // 이메일 인증 여부
        @JsonProperty("email_verified")
                Boolean emailVerified,
        // 로케일 (언어-국가 코드)
        String locale
) {
}

