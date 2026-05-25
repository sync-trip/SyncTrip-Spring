package com.sync.dto.holiday;

import java.time.LocalDate;

/**
 * Nager.Date API 응답에서 필요한 필드만 추출한 공휴일 정보 DTO
 */
public record HolidayInfo(
        LocalDate date,
        String localName,  // 현지어 공휴일명 (예: 成人の日)
        String name        // 영문 공휴일명 (예: Coming of Age Day)
) {
}
