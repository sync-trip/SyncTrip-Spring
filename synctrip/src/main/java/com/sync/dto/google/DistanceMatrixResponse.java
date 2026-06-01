package com.sync.dto.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Google Distance Matrix API 응답 DTO
 * GET https://maps.googleapis.com/maps/api/distancematrix/json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DistanceMatrixResponse(
        String status,
        List<Row> rows
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(List<Element> elements) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Element(String status, DurationValue duration, DurationValue distance) {}

    /** value는 초(duration) 또는 미터(distance) 단위 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DurationValue(int value, String text) {}
}
