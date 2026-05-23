package com.sync.dto.band;

import com.sync.domain.band.BandStatus;
import com.sync.domain.band.TravelStyle;
import java.time.LocalDate;

public record BandResponse(
    Long id,
    String name,
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    String inviteCode,
    BandStatus status,
    boolean isOwner,
    boolean isOverseas,
    TravelStyle travelStyle,
    String accommodationName,
    int memberCount,
    // 여행지 썸네일 이미지 URL (NULL=이미지 없음, 플레이스홀더 표시)
    String thumbnailUrl
) {
}
