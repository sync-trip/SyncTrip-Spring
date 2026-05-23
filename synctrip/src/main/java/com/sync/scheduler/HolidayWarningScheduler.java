package com.sync.scheduler;

import com.sync.domain.band.Band;
import com.sync.domain.notification.NotificationType;
import com.sync.dto.holiday.HolidayInfo;
import com.sync.repository.BandRepository;
import com.sync.service.HolidayService;
import com.sync.service.NotificationService;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-7 공휴일 사전 알림 스케줄러
 *
 * 매일 새벽 3시 10분에 7일 뒤 시작하는 해외 밴드를 조회하여
 * 여행 기간 내 현지 공휴일이 있으면 밴드 전원에게 알림을 발송합니다.
 *
 * - 일정 생성 시점 알림의 안전망 역할 (날짜가 많이 남아서 잊었을 경우 대비)
 * - 기존 NotificationCleanupScheduler(03:00) 이후 실행하여 간섭 방지
 */
@Component
public class HolidayWarningScheduler {

    private static final Logger log = LoggerFactory.getLogger(HolidayWarningScheduler.class);
    private static final int DAYS_BEFORE_TRIP = 7;

    private final BandRepository bandRepository;
    private final HolidayService holidayService;
    private final NotificationService notificationService;

    public HolidayWarningScheduler(BandRepository bandRepository,
                                   HolidayService holidayService,
                                   NotificationService notificationService) {
        this.bandRepository = bandRepository;
        this.holidayService = holidayService;
        this.notificationService = notificationService;
    }

    // 매일 새벽 3시 10분 실행 (cron: 초 분 시 일 월 요일)
    @Scheduled(cron = "0 10 3 * * *")
    @Transactional
    public void sendHolidayWarnings() {
        LocalDate targetDate = LocalDate.now().plusDays(DAYS_BEFORE_TRIP);
        List<Band> bands = bandRepository.findOverseasBandsByStartDate(targetDate);

        if (bands.isEmpty()) return;

        int notified = 0;
        for (Band band : bands) {
            List<HolidayInfo> holidays = holidayService.getHolidaysInRange(
                    band.getCountryCode(), band.getStartDate(), band.getEndDate());
            if (holidays.isEmpty()) continue;

            notificationService.notifyAll(band.getId(), NotificationType.HOLIDAY_WARNING,
                    holidayService.buildHolidayMessage(band, holidays));
            notified++;
        }

        if (notified > 0) {
            log.info("D-7 공휴일 알림 발송 완료: {}개 밴드 (여행 시작일: {})", notified, targetDate);
        }
    }
}
