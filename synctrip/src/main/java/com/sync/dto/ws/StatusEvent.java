package com.sync.dto.ws;

import com.sync.domain.band.BandStatus;

public record StatusEvent(Long bandId, BandStatus status) {}
