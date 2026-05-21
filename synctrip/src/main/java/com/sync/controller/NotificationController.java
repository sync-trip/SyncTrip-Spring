package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.notification.FcmTokenRequest;
import com.sync.dto.notification.NotificationResponse;
import com.sync.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/users/fcm-token")
    public ResponseEntity<Void> registerFcmToken(
            @LoginUser Long userId,
            @Valid @RequestBody FcmTokenRequest request
    ) {
        notificationService.registerFcmToken(userId, request.token());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResponse>> getNotifications(@LoginUser Long userId) {
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@LoginUser Long userId) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @LoginUser Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.markRead(userId, notificationId);
        return ResponseEntity.ok().build();
    }
}
