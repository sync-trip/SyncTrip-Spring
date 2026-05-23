package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.dto.holiday.HolidayInfo;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Nager.Date API를 이용한 공휴일 조회 서비스
 *
 * - 무료, 인증 불필요, ISO 3166-1 alpha-2 국가코드 사용 (KR, JP, US 등)
 * - API 응답은 연도+국가코드 단위로 캐싱 (공휴일은 연간 고정)
 * - 해외 밴드(is_overseas=TRUE)에서만 호출
 */
@Service
public class HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);
    private static final String NAGER_API_URL = "https://date.nager.at/api/v3/PublicHolidays/{year}/{countryCode}";
    private static final DateTimeFormatter MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("M월 d일");

    private final RestTemplate restTemplate;

    public HolidayService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 연도별 공휴일 전체 조회 (달력 표시용)
     * - 결과는 캐시에 저장되어 동일 국가+연도 재호출 시 API를 다시 호출하지 않음
     */
    @Cacheable(value = "holidays", key = "#countryCode + '_' + #year")
    public List<HolidayInfo> getHolidaysByYear(String countryCode, int year) {
        try {
            ResponseEntity<NagerHolidayResponse[]> response = restTemplate.exchange(
                    NAGER_API_URL,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<NagerHolidayResponse[]>() {},
                    year, countryCode
            );
            if (response.getBody() == null) return Collections.emptyList();
            return Arrays.stream(response.getBody())
                    .map(r -> new HolidayInfo(LocalDate.parse(r.date()), r.localName(), r.name()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Nager.Date API 호출 실패 (countryCode={}, year={}): {}", countryCode, year, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 여행 기간 내 공휴일만 필터링해서 반환
     */
    public List<HolidayInfo> getHolidaysInRange(String countryCode, LocalDate start, LocalDate end) {
        // 기간이 두 해에 걸치는 경우 양쪽 연도 모두 조회
        List<HolidayInfo> holidays = getHolidaysByYear(countryCode, start.getYear());
        if (start.getYear() != end.getYear()) {
            holidays = new java.util.ArrayList<>(holidays);
            holidays.addAll(getHolidaysByYear(countryCode, end.getYear()));
        }
        return holidays.stream()
                .filter(h -> !h.date().isBefore(start) && !h.date().isAfter(end))
                .collect(Collectors.toList());
    }

    /**
     * 밴드 여행 기간 내 공휴일 알림 메시지 생성
     * - 1개: "여행 기간 중 MM월 DD일(현지명)이 공휴일입니다. 일부 시설이 운영을 중단할 수 있어요."
     * - 2개 이상: "여행 기간 중 N개의 현지 공휴일이 있습니다. (MM월 DD일 현지명, ...)"
     */
    public String buildHolidayMessage(Band band, List<HolidayInfo> holidays) {
        if (holidays.isEmpty()) return "";
        if (holidays.size() == 1) {
            HolidayInfo h = holidays.get(0);
            return band.getName() + " 여행 기간 중 " + h.date().format(MONTH_DAY_FORMATTER)
                    + "(" + h.localName() + ")이 공휴일입니다. 일부 시설이 운영을 중단할 수 있어요. 📅";
        }
        String dates = holidays.stream()
                .map(h -> h.date().format(MONTH_DAY_FORMATTER) + " " + h.localName())
                .collect(Collectors.joining(", "));
        return band.getName() + " 여행 기간 중 " + holidays.size() + "개의 현지 공휴일이 있습니다. ("
                + dates + ") 📅";
    }

    /**
     * Nager.Date API 응답 역직렬화용 내부 레코드
     */
    private record NagerHolidayResponse(String date, String localName, String name) {}
}
