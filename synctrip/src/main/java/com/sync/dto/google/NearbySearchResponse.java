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
            Double rating,
            String formattedAddress,
            RegularOpeningHours regularOpeningHours,
            List<Photo> photos,
            List<AddressComponent> addressComponents
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddressComponent(
            String longText,
            String shortText,
            List<String> types
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Photo(String name) {}

    public record LocalizedText(String text, String languageCode) {}

    public record LatLng(double latitude, double longitude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegularOpeningHours(List<Period> periods) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Period(TimePoint open, TimePoint close) {}

        public record TimePoint(int day, int hour, int minute) {}
    }
}
