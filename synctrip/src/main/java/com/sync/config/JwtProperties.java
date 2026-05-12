package com.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        String secret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) {
}

