package com.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.maps")
public record GoogleMapsProperties(
        String apiKey,
        String placesBaseUrl
) {
}
