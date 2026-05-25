package com.sync.controller;

import com.sync.dto.holiday.HolidayInfo;
import com.sync.service.HolidayService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Year;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공휴일 조회 API
 *
 * 밴드 생성 전 날짜 선택 화면에서 달력에 공휴일을 표시하기 위해 사용.
 * 국가 코드 선택 후 해당 연도의 공휴일 전체를 반환하며 앱 달력에 마킹한다.
 */
@RestController
@RequestMapping("/api/holidays")
@Validated
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    /**
     * 국가+연도별 공휴일 목록 조회
     *
     * GET /api/holidays?countryCode=JP&year=2026
     * - countryCode: ISO 3166-1 alpha-2 (예: JP, US, FR)
     * - year: 조회 연도 (2020~2030)
     */
    @GetMapping
    public ResponseEntity<List<HolidayInfo>> getHolidays(
            @RequestParam @NotBlank String countryCode,
            @RequestParam @Min(2020) @Max(2030) int year
    ) {
        return ResponseEntity.ok(holidayService.getHolidaysByYear(countryCode.toUpperCase(), year));
    }
}