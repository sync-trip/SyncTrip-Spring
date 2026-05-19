package com.sync.dto.band;

import com.sync.domain.band.BandRole;
import java.time.LocalDateTime;

public record BandMemberResponse(
    Long userId,
    String name,
    String profileImageUrl,
    BandRole role,
    boolean isReady,
    LocalDateTime joinedAt,
    int bookmarkCount
) {
}
