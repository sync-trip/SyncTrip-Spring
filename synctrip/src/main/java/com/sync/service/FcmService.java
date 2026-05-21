package com.sync.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * FCM(Firebase Cloud Messaging) 푸시 알림 발송 서비스
 *
 * 안드로이드 기기에 푸시 알림을 발송합니다.
 * 아래 경우에는 발송을 건너뜁니다:
 *   - FCM 토큰이 없는 경우 (로그인 후 토큰 미등록 상태)
 *   - Firebase가 초기화되지 않은 경우 (FIREBASE_CREDENTIALS_JSON 미설정)
 *
 * 발송 결과는 로그로 확인합니다:
 *   성공 → INFO: "FCM 발송 성공: token=..., messageId=..."
 *   실패 → ERROR: "FCM 발송 실패: token=..., error=..."
 */
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    /**
     * 단일 기기에 푸시 알림 발송
     *
     * @param token FCM 디바이스 토큰 (POST /api/users/fcm-token으로 등록된 값)
     * @param title 알림 타이틀 (NotificationType.getTitle()에서 가져옴)
     * @param body  알림 본문
     */
    public void send(String token, String title, String body) {
        // FCM 토큰 미등록 유저는 건너뜀
        if (token == null || token.isBlank()) return;

        // Firebase 미초기화 시 ERROR 로그 후 종료 (FIREBASE_CREDENTIALS_JSON 설정 필요)
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
            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 발송 성공: token={}, messageId={}", token, messageId);
        } catch (Exception e) {
            log.error("FCM 발송 실패: token={}, error={}", token, e.getMessage());
        }
    }
}
