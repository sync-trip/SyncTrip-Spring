package com.sync.dto.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NearbySearchResponse(List<Place> places) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(
            String id,
            List<String> types,
            LocalizedText displayName,
            LatLng location,
            RegularOpeningHours regularOpeningHours
    ) {}

    public record LocalizedText(String text, String languageCode) {}

    public record LatLng(double latitude, double longitude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegularOpeningHours(List<Period> periods) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Period(TimePoint open, TimePoint close) {}

        public record TimePoint(int day, int hour, int minute) {}
    }
}
