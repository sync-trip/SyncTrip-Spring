package com.sync.dto.destination;

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
) {}
