package com.sync.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Firebase 초기화 설정
 *
 * FIREBASE_CREDENTIALS_JSON 환경변수가 설정된 경우에만 Firebase를 초기화합니다.
 * 값이 없거나 비어있으면 초기화를 건너뛰고, FCM 발송 시도 시 FcmService에서 ERROR 로그를 남깁니다.
 *
 * 초기화 흐름:
 *   base64 디코딩 → GoogleCredentials 생성 → FirebaseApp.initializeApp()
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    private final FcmProperties fcmProperties;

    public FirebaseConfig(FcmProperties fcmProperties) {
        this.fcmProperties = fcmProperties;
    }

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        String credJson = fcmProperties.credentialsJson();

        // 자격증명이 없으면 Firebase 비활성화 (로컬/테스트 환경 대응)
        if (credJson == null || credJson.isBlank()) {
            log.info("FIREBASE_CREDENTIALS_JSON 미설정 - FCM 비활성화");
            return null;
        }

        // 이미 초기화된 경우 재초기화 방지 (서버 재시작 등)
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        byte[] decoded = Base64.getDecoder().decode(credJson);
        GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
        return FirebaseApp.initializeApp(options);
    }
}
