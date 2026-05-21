package com.sync.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    public void send(String token, String title, String body) {
        if (token == null || token.isBlank()) return;
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("FCM 미초기화 - 푸시 알림 건너뜀 (개발/테스트 환경)");
            return;
        }
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            log.warn("FCM 발송 실패: {}", e.getMessage());
        }
    }
}
