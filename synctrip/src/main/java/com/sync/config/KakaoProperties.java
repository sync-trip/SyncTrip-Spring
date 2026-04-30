package com.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope
) {
}

