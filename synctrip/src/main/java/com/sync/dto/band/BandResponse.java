package com.sync.dto.band;

import java.time.LocalDate;

public record BandResponse(
    Long id,
    String name,
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    String inviteCode
) {
}
