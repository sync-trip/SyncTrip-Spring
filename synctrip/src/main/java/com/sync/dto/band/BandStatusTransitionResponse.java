package com.sync.dto.band;

import com.sync.domain.band.BandStatus;

public record BandStatusTransitionResponse(
        Long bandId,
        BandStatus previousStatus,
        BandStatus currentStatus
) {
}
