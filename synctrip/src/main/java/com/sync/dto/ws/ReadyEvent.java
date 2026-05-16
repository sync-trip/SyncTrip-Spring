package com.sync.dto.ws;

import com.sync.domain.band.BandStatus;

public record ReadyEvent(
    Long userId,
    boolean isReady,
    long readyCount,
    long totalCount,
    boolean allReady,
    BandStatus bandStatus
) {}
