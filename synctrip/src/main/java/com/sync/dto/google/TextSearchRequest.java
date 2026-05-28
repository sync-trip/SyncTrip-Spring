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
    /** locationBias와 달리 반경 밖 결과를 완전히 제외한다 */
    public record LocationRestriction(Circle circle) {}
    public record Circle(LatLng center, double radius) {}
    public record LatLng(double latitude, double longitude) {}
}
