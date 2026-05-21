package com.sync.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.sync.config.GoogleAuthProperties;
import com.sync.dto.google.GoogleUserResponse;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/**
 * Google OAuth 인증 서비스
 * 안드로이드에서 전달받은 Google ID Token을 검증하고 사용자 정보 추출
 *
 * Google Sign-In 플로우:
 * 1. 안드로이드 앱이 Google Sign-In SDK로 ID Token 획득
 * 2. 앱에서 백엔드로 ID Token 전송
 * 3. 백엔드에서 ID Token의 JWT 서명을 검증 (GoogleIdTokenVerifier 사용)
 * 4. 검증된 토큰에서 사용자 정보(sub, email, name, picture) 추출
 */
@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = new GsonFactory();

    private final GoogleAuthProperties googleAuthProperties;
    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(GoogleAuthProperties googleAuthProperties) {
        this.googleAuthProperties = googleAuthProperties;
        // Google ID Token 검증기 생성
        // - clientId: Google Cloud Console에서 발급받은 클라이언트 ID
        // - HTTP Transport와 JSON Factory: Google API 클라이언트 라이브러리 필수 설정
        this.verifier = new GoogleIdTokenVerifier.Builder(HTTP_TRANSPORT, JSON_FACTORY)
                .setAudience(Collections.singletonList(googleAuthProperties.id()))
                .build();
    }

    /**
     * Google ID Token을 검증하고 사용자 정보 추출
     *
     * 검증 과정:
     * 1. JWT 형식 확인
     * 2. JWT 서명 검증 (Google 공개키 사용)
     * 3. 토큰 만료 시간 확인
     * 4. Audience (aud) claim 검증
     *
     * @param idToken Google ID Token (안드로이드 Google Sign-In SDK로부터)
     * @return 사용자 정보 (sub, email, name, picture 등)
     * @throws ResponseStatusException 토큰이 유효하지 않으면 예외 발생
     */
    public GoogleUserResponse validateAndGetUserInfo(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new ResponseStatusException(BAD_REQUEST, "Google ID Token이 필요합니다.");
        }

        try {
            // GoogleIdTokenVerifier를 사용하여 ID Token 검증
            // - JWT 서명 검증 (Google 공개키로)
            // - 만료 시간 확인
            // - Audience 검증
            GoogleIdToken token = verifier.verify(idToken);

            if (token == null) {
                log.warn("Google ID Token verification failed: token is null");
                throw new ResponseStatusException(BAD_REQUEST, "유효하지 않은 Google ID Token입니다.");
            }

            GoogleIdToken.Payload payload = token.getPayload();

            // 사용자 고유 ID (Google 계정마다 고유)
            String userId = payload.getSubject();

            // 사용자 정보 추출
            String email = payload.getEmail();
            String emailVerified = payload.getEmailVerified() != null && payload.getEmailVerified() ? "true" : "false";
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");
            String locale = (String) payload.get("locale");

            GoogleUserResponse response = new GoogleUserResponse(
                    userId,
                    name != null ? name : "",
                    email,
                    picture != null ? picture : "",
                    payload.getEmailVerified() != null && payload.getEmailVerified(),
                    locale != null ? locale : ""
            );

            log.info("Google ID Token validated successfully. User: {}, Email: {}", userId, email);
            return response;

        } catch (Exception ex) {
            log.error("Failed to validate Google ID Token: {}", ex.getMessage());
            if (ex instanceof ResponseStatusException) {
                throw (ResponseStatusException) ex;
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Google ID Token 검증에 실패했습니다: " + ex.getMessage(), ex);
        }
    }
}





