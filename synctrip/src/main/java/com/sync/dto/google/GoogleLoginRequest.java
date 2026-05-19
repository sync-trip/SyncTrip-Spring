package com.sync.dto.google;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Google OAuth 로그인 요청 DTO
 * 안드로이드에서 Google Sign-In으로 받은 ID Token을 전달받음
 */
public record GoogleLoginRequest(
        @JsonProperty("id_token")
        String idToken
) {
}

