package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.notification.FcmTokenRequest;
import com.sync.dto.notification.NotificationResponse;
import com.sync.dto.notification.NotificationSettingRequest;
import com.sync.dto.notification.NotificationSettingResponse;
import com.sync.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 API 컨트롤러
 *
 * 엔드포인트 목록:
 *   POST   /api/users/fcm-token                    FCM 디바이스 토큰 등록
 *   GET    /api/users/notification-settings         알림 수신 설정 조회
 *   PATCH  /api/users/notification-settings         알림 수신 설정 변경
 *   GET    /api/notifications                        내 알림 목록 조회 (최신순, 페이지네이션)
 *   GET    /api/notifications/unread-count           미읽음 알림 개수 조회
 *   PATCH  /api/notifications/{id}/read              알림 1건 읽음 처리
 *   PATCH  /api/notifications/read-all               전체 읽음 처리
 *   DELETE /api/notifications/{id}                   알림 1건 삭제
 *   DELETE /api/notifications                        알림 전체 삭제
 */
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * FCM 디바이스 토큰 등록
     * 앱 실행 시 Firebase SDK가 발급한 토큰을 서버에 저장합니다.
     * 저장된 토큰으로 이후 푸시 알림이 해당 기기에 전달됩니다.
     */
    @PostMapping("/users/fcm-token")
    public ResponseEntity<Void> registerFcmToken(
            @LoginUser Long userId,
            @Valid @RequestBody FcmTokenRequest request
    ) {
        notificationService.registerFcmToken(userId, request.token());
        return ResponseEntity.ok().build();
    }

    /**
     * 알림 수신 설정 조회
     * 알림 설정 화면 진입 시 각 토글의 현재 상태를 가져옵니다.
     * 응답 예시: { "voteStarted": true, "scheduleUpdated": false, ... }
     */
    @GetMapping("/users/notification-settings")
    public ResponseEntity<NotificationSettingResponse> getNotificationSettings(@LoginUser Long userId) {
        return ResponseEntity.ok(notificationService.getNotificationSettings(userId));
    }

    /**
     * 알림 수신 설정 변경
     * FCM 푸시 알림을 타입별로 on/off합니다.
     * In-App 알림(앱 내 알림 센터)은 끔 여부와 관계없이 항상 저장됩니다.
     *
     * 요청 예시: { "type": "VOTE_STARTED", "enabled": false }
     */
    @PatchMapping("/users/notification-settings")
    public ResponseEntity<Void> updateNotificationSetting(
            @LoginUser Long userId,
            @Valid @RequestBody NotificationSettingRequest request
    ) {
        notificationService.updateNotificationSetting(userId, request.type(), request.enabled());
        return ResponseEntity.ok().build();
    }

    /**
     * 내 알림 목록 조회 (최신순, 페이지네이션)
     * - page: 0부터 시작, size: 한 번에 받을 개수 (기본 20)
     * - 응답 목록 크기가 size보다 작으면 마지막 페이지입니다.
     */
    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @LoginUser Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(notificationService.getNotifications(userId, page, size));
    }

    /**
     * 미읽음 알림 개수 조회
     * 앱 아이콘 뱃지 숫자 표시에 사용합니다.
     * 응답 예시: { "count": 3 }
     */
    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@LoginUser Long userId) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    /**
     * 알림 1건 읽음 처리
     * 본인 알림만 읽음 처리할 수 있습니다. 다른 유저의 알림이면 403 반환.
     */
    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @LoginUser Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.markRead(userId, notificationId);
        return ResponseEntity.ok().build();
    }

    /**
     * 전체 읽음 처리
     * 미읽음 알림을 한 번에 모두 읽음으로 변경합니다.
     * 응답 예시: { "updated": 5 }  (읽음 처리된 알림 개수)
     */
    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@LoginUser Long userId) {
        int updated = notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    /**
     * 알림 1건 삭제
     * 본인 알림만 삭제할 수 있습니다. 존재하지 않거나 다른 유저의 알림이면 404 반환.
     */
    @DeleteMapping("/notifications/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @LoginUser Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.deleteNotification(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 알림 전체 삭제
     * 내 알림을 모두 삭제합니다 (읽음/미읽음 구분 없이).
     */
    @DeleteMapping("/notifications")
    public ResponseEntity<Void> deleteAllNotifications(@LoginUser Long userId) {
        notificationService.deleteAllNotifications(userId);
        return ResponseEntity.noContent().build();
    }
}
