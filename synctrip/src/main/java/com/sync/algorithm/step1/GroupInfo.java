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
        boolean isOverseas,
        // 숙소 좌표 — null이면 숙소 미입력(현행 동작 유지: 첫 우선순위 장소 출발)
        Double accommodationLat,
        Double accommodationLng
) {}