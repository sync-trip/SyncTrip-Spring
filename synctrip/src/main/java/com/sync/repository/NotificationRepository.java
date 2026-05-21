package com.sync.repository;

import com.sync.domain.notification.Notification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 특정 유저의 알림 목록을 최신순으로 조회 (페이지네이션 적용)
    // 인덱스: idx_notifications_user_created (user_id, created_at DESC)
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId ORDER BY n.createdAt DESC")
    List<Notification> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    // 특정 유저의 미읽음 알림 개수 (앱 뱃지 숫자용)
    // 인덱스: idx_notifications_user_unread (user_id, is_read)
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.isRead = false")
    long countUnreadByUserId(@Param("userId") Long userId);

    // 특정 유저의 미읽음 알림 전체를 읽음으로 일괄 변경 (전체 읽음 처리)
    // @Modifying: SELECT가 아닌 UPDATE/DELETE 쿼리임을 명시
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllReadByUserId(@Param("userId") Long userId);

    // 알림 1건 삭제 (본인 알림인지 검증 포함)
    // 반환값: 삭제된 행 수 (0이면 존재하지 않거나 다른 유저의 알림)
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // 특정 유저의 알림 전체 삭제
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    // TTL 정책: 지정 시각보다 오래된 알림 일괄 삭제 (스케줄러에서 호출)
    // 반환값: 삭제된 행 수 (모니터링 로그용)
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :before")
    int deleteOlderThan(@Param("before") java.time.LocalDateTime before);
}
