package com.sync.scheduler;

import com.sync.repository.NotificationRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오래된 알림 자동 삭제 스케줄러
 *
 * 매일 새벽 3시에 30일이 지난 알림을 일괄 삭제합니다.
 * - 알림은 읽었든 안 읽었든 30일 후 삭제됩니다.
 * - 삭제 건수를 로그로 남겨 모니터링할 수 있습니다.
 */
@Component
public class NotificationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupScheduler.class);

    private static final int RETENTION_DAYS = 30;

    private final NotificationRepository notificationRepository;

    public NotificationCleanupScheduler(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // 매일 새벽 3시 실행 (cron: 초 분 시 일 월 요일)
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = notificationRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("오래된 알림 정리 완료: {}건 삭제 (기준: {}일 이전)", deleted, RETENTION_DAYS);
        }
    }
}
