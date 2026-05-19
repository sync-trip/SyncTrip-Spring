package com.sync.dto.band;

import com.sync.domain.band.TravelStyle;
import java.time.LocalDate;

public record BandUpdateRequest(
    String name,
    String destination,
    double destinationLat,
    double destinationLng,
    String countryCode,
    boolean overseas,
    LocalDate startDate,
    LocalDate endDate,
    TravelStyle travelStyle,
    String accommodationName,
    Double accommodationLat,
    Double accommodationLng
) {}
