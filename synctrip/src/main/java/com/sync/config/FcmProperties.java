package com.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FCM(Firebase Cloud Messaging) 설정값
 *
 * application.yml의 fcm.credentials-json 값을 읽어옵니다.
 * 해당 값은 Firebase 서비스 계정 JSON을 base64로 인코딩한 문자열입니다.
 *
 * 발급 방법:
 *   Firebase 콘솔 → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성
 *   다운로드한 JSON 파일을 base64로 인코딩: base64 -w 0 firebase-service-account.json
 *   결과 문자열을 FIREBASE_CREDENTIALS_JSON 환경변수에 저장
 *
 * 값이 없으면 FirebaseConfig에서 초기화를 건너뛰고 FCM은 비활성화됩니다.
 */
@ConfigurationProperties(prefix = "fcm")
public record FcmProperties(
        String credentialsJson
) {}
