package com.sync.dto.google;

import java.util.List;

public record TextSearchRequest(
        String textQuery,
        LocationBias locationBias,
        List<String> includedTypes,
        int maxResultCount,
        String languageCode
) {
    public record LocationBias(Circle circle) {}
    public record Circle(LatLng center, double radius) {}
    public record LatLng(double latitude, double longitude) {}
}
