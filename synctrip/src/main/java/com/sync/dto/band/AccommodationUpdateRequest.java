package com.sync.dto.band;

/**
 * 숙소 변경 요청 DTO
 * - accommodationName: 숙소명 (null 허용 — 숙소 없음 처리)
 * - accommodationLat / accommodationLng: 숙소 좌표 (null이면 좌표 없음)
 */
public record AccommodationUpdateRequest(
        String accommodationName,
        Double accommodationLat,
        Double accommodationLng
) {}
