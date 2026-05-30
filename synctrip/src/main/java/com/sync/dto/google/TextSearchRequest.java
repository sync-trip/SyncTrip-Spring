package com.sync.dto.google;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextSearchRequest(
        String textQuery,
        LocationBias locationBias,
        LocationRestriction locationRestriction,
        int maxResultCount,
        String languageCode,
        String includedType
) {
    public record LocationBias(Circle circle) {}
    /** circle은 미지원 — rectangle만 사용 가능 */
    public record LocationRestriction(Rectangle rectangle) {}
    public record Circle(LatLng center, double radius) {}
    public record Rectangle(LatLng low, LatLng high) {}
    public record LatLng(double latitude, double longitude) {}
}
