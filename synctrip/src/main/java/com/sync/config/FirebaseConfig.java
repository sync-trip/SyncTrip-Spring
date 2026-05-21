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
        if (credJson == null || credJson.isBlank()) {
            log.info("FIREBASE_CREDENTIALS_JSON 미설정 - FCM 비활성화");
            return null;
        }
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
