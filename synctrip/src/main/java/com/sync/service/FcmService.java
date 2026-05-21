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
            log.error("FCM 미초기화 상태에서 발송 시도 - FIREBASE_CREDENTIALS_JSON 설정을 확인하세요. token={}", token);
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
            log.error("FCM 발송 실패: token={}, error={}", token, e.getMessage());
        }
    }
}
