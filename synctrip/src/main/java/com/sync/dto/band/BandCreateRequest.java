package com.sync.dto.band;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BandCreateRequest(
    @NotBlank(message = "여행 이름을 입력해주세요")
    String name,

    @NotNull(message = "시작 날짜를 선택해주세요")
    LocalDate startDate,

    @NotNull(message = "종료 날짜를 선택해주세요")
    LocalDate endDate,

    @NotBlank(message = "여행지를 선택해주세요")
    String destination,

    double destinationLat,
    double destinationLng,

    @NotBlank(message = "국가 코드를 입력해주세요")
    String countryCode,
    boolean overseas
) {
}
