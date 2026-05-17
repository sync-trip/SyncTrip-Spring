package com.sync.dto.band;

import java.time.LocalDate;

public record BandUpdateRequest(
    String name,
    String destination,
    double destinationLat,
    double destinationLng,
    String countryCode,
    boolean overseas,
    LocalDate startDate,
    LocalDate endDate
) {}
