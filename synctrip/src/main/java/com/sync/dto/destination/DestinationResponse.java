package com.sync.dto.destination;

// @Cacheable 직렬화를 위해 Serializable 구현 (JdkSerializationRedisSerializer 요구사항)
public record DestinationResponse(
        String name,
        String country,
        String countryCode,
        double lat,
        double lng,
        boolean overseas,
        String region,
        String description,
        String thumbnailUrl
) implements java.io.Serializable {}
