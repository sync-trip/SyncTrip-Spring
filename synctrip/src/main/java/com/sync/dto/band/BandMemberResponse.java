package com.sync.dto.band;

import com.sync.domain.band.BandRole;

public record BandMemberResponse(
    Long userId,
    String name,
    String profileImageUrl,
    BandRole role,
    boolean isReady
) {
}
