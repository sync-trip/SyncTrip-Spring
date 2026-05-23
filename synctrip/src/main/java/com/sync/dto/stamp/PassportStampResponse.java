package com.sync.dto.stamp;

import com.sync.domain.stamp.PassportStamp;
import java.time.LocalDateTime;

public record PassportStampResponse(
        Long id,
        Long bandId,
        String bandName,
        String city,
        String countryCode,
        LocalDateTime stampedAt
) {
    public static PassportStampResponse from(PassportStamp stamp) {
        return new PassportStampResponse(
                stamp.getId(),
                stamp.getBand().getId(),
                stamp.getBand().getName(),
                stamp.getCity(),
                stamp.getCountryCode(),
                stamp.getStampedAt()
        );
    }
}
