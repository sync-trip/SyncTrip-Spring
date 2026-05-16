package com.sync.algorithm.step1;

import com.sync.algorithm.TravelStyle;
import java.time.LocalDate;

public record GroupInfo(
        long id,
        double destinationLat,
        double destinationLng,
        TravelStyle travelStyle,
        LocalDate startDate,
        LocalDate endDate,
        boolean isOverseas
) {}