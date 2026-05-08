package com.sync.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

public record KakaoLoginRequest(
        // Android/Swagger에서는 accessToken, 일부 OAuth 예제에서는 access_token을 쓰므로 둘 다 허용
        @JsonProperty("accessToken")
        @JsonAlias({"accessToken", "access_token"})
        String accessToken
) {
}

