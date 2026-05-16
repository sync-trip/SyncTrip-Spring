package com.sync.dto.band;

import java.time.LocalDate;

public record BandCreateRequest(
    String name,
    LocalDate startDate,
    LocalDate endDate,
    String destination,
    double destinationLat,
    double destinationLng,
    String countryCode,
    boolean overseas
) {
}
