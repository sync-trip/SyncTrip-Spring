package com.sync.dto.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Google Directions API 응답 DTO
 * GET https://maps.googleapis.com/maps/api/directions/json?mode=transit
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DirectionsResponse(
        String status,
        List<Route> routes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(List<Leg> legs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(
            DurationValue duration,
            List<Step> steps
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            @JsonProperty("travel_mode") String travelMode,
            DurationValue duration,
            @JsonProperty("transit_details") TransitDetails transitDetails
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransitDetails(
            Line line,
            @JsonProperty("num_stops") int numStops
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            @JsonProperty("short_name") String shortName,
            String name,
            Vehicle vehicle
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vehicle(String type, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DurationValue(int value, String text) {}
}
