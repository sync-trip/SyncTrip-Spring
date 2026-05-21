package com.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exchangerate")
public record ExchangeRateApiProperties(
        String apiKey,
        String baseUrl
) {
}
