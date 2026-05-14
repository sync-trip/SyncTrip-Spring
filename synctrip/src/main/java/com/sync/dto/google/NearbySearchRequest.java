package com.sync.dto.google;

import java.util.List;

public record NearbySearchRequest(
        LocationRestriction locationRestriction,
        List<String> includedTypes,
        int maxResultCount,
        String languageCode
) {
    public record LocationRestriction(Circle circle) {}
    public record Circle(LatLng center, double radius) {}
    public record LatLng(double latitude, double longitude) {}
}
