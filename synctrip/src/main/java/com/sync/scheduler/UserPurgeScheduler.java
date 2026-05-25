package com.sync.scheduler;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 탈퇴 회원 하드 삭제 스케줄러 — APP_USER_PURGE_ENABLED=true 일 때만 활성화
@Component
@ConditionalOnProperty(name = "app.user-purge.enabled", havingValue = "true")
public class UserPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserPurgeScheduler.class);

    private final JdbcTemplate jdbc;
    private final long thresholdSeconds;

    public UserPurgeScheduler(JdbcTemplate jdbc,
                               @Value("${app.user-purge.threshold-seconds:2592000}") long thresholdSeconds) {
        this.jdbc = jdbc;
        this.thresholdSeconds = thresholdSeconds;
    }

    // 탈퇴 후 thresholdSeconds 이상 경과한 회원을 주기적으로 하드 삭제
    @Scheduled(fixedDelayString = "${app.user-purge.check-interval-ms:10000}")
    @Transactional
    public void purgeDeletedUsers() {
        List<Long> targets = jdbc.queryForList(
                "SELECT user_id FROM users WHERE is_deleted = TRUE AND deleted_at < DATE_SUB(NOW(), INTERVAL ? SECOND)",
                Long.class, thresholdSeconds
        );
        for (Long userId : targets) {
            purgeUser(userId);
        }
    }

    private void purgeUser(Long userId) {
        log.info("탈퇴 회원 하드 삭제 시작: userId={}", userId);

        // 알림
        jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
        // 지출 분담 (본인이 분담자인 항목)
        jdbc.update("DELETE FROM expense_members WHERE user_id = ?", userId);
        // 지출 분담 (본인이 결제자인 지출에 딸린 분담 내역)
        jdbc.update("DELETE FROM expense_members WHERE expense_id IN (SELECT expense_id FROM expenses WHERE payer_id = ?)", userId);
        // 지출
        jdbc.update("DELETE FROM expenses WHERE payer_id = ?", userId);
        // 앨범 사진
        jdbc.update("DELETE FROM album_photos WHERE uploader_id = ?", userId);
        // 여권 스탬프
        jdbc.update("DELETE FROM passport_stamps WHERE user_id = ?", userId);
        // 장바구니
        jdbc.update("DELETE FROM place_bookmarks WHERE user_id = ?", userId);
        // 투표
        jdbc.update("DELETE FROM votes WHERE user_id = ?", userId);
        // 그룹 멤버십 제거
        jdbc.update("DELETE FROM group_members WHERE user_id = ?", userId);
        // 방장인 그룹 처리 (다른 멤버에게 이전 or 그룹 삭제)
        handleOwnedGroups(userId);
        // 사용자 삭제
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);

        log.info("탈퇴 회원 하드 삭제 완료: userId={}", userId);
    }

    private void handleOwnedGroups(Long userId) {
        List<Long> ownedGroupIds = jdbc.queryForList(
                "SELECT group_id FROM user_groups WHERE owner_id = ? AND is_deleted = FALSE",
                Long.class, userId
        );
        for (Long groupId : ownedGroupIds) {
            // 다른 활성 멤버가 있으면 방장 이전, 없으면 그룹 전체 삭제
            List<Long> others = jdbc.queryForList(
                    "SELECT user_id FROM group_members WHERE group_id = ? AND user_id != ? AND is_deleted = FALSE LIMIT 1",
                    Long.class, groupId, userId
            );
            if (!others.isEmpty()) {
                jdbc.update("UPDATE user_groups SET owner_id = ? WHERE group_id = ?", others.get(0), groupId);
                log.info("그룹 방장 이전: groupId={}, newOwner={}", groupId, others.get(0));
            } else {
                deleteGroup(groupId);
            }
        }
    }

    private void deleteGroup(Long groupId) {
        jdbc.update("DELETE FROM schedule_alts WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM schedules WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM votes WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM place_bookmarks WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM expense_members WHERE expense_id IN (SELECT expense_id FROM expenses WHERE group_id = ?)", groupId);
        jdbc.update("DELETE FROM expenses WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM notifications WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM group_exchange_rates WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM group_finance WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM group_vote_info WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM group_members WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM user_groups WHERE group_id = ?", groupId);
        log.info("그룹 하드 삭제 완료: groupId={}", groupId);
    }
}
