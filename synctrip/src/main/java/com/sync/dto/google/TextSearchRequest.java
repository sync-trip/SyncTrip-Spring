package com.sync.dto.google;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextSearchRequest(
        String textQuery,
        LocationBias locationBias,
        int maxResultCount,
        String languageCode
) {
    public record LocationBias(Circle circle) {}
    public record Circle(LatLng center, double radius) {}
    public record LatLng(double latitude, double longitude) {}
}
