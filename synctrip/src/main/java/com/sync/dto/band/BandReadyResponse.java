package com.sync.dto.band;

import com.sync.domain.band.BandStatus;

public record BandReadyResponse(
        Long bandId,
        Long userId,
        boolean isReady,
        long readyCount,
        long totalCount,
        boolean allReady,
        BandStatus bandStatus
) {
}
