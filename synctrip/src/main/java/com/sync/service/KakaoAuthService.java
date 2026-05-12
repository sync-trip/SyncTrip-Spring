package com.sync.service;

import com.sync.config.KakaoProperties;
import com.sync.dto.kakao.KakaoTokenResponse;
import com.sync.dto.kakao.KakaoUserResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class KakaoAuthService {

    private static final Logger log = LoggerFactory.getLogger(KakaoAuthService.class);

    private final RestTemplate restTemplate;
    private final KakaoProperties kakaoProperties;

    public KakaoAuthService(RestTemplate restTemplate, KakaoProperties kakaoProperties) {
        this.restTemplate = restTemplate;
        this.kakaoProperties = kakaoProperties;
    }

    public KakaoTokenResponse exchangeToken(String code, String state) {
        // ... (existing validation)
        if (!StringUtils.hasText(code)) {
            throw new ResponseStatusException(BAD_REQUEST, "카카오 인가 코드(code)가 필요합니다.");
        }
        // ... (rest of validation)

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakaoProperties.clientId());
        form.add("redirect_uri", kakaoProperties.redirectUri());
        form.add("code", code);

        if (StringUtils.hasText(kakaoProperties.clientSecret())) {
            form.add("client_secret", kakaoProperties.clientSecret());
        }
        if (StringUtils.hasText(state)) {
            form.add("state", state);
        }

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(form, headers);

        try {
            log.info("Exchanging Kakao token with code: {}", code);
            ResponseEntity<KakaoTokenResponse> response = restTemplate.exchange(
                    kakaoProperties.tokenUri(),
                    HttpMethod.POST,
                    requestEntity,
                    KakaoTokenResponse.class
            );

            KakaoTokenResponse body = response.getBody();
            if (body == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "카카오 토큰 응답이 비어 있습니다.");
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            log.error("Failed to exchange Kakao token. Status: {}, Response: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "카카오 토큰 발급에 실패했습니다: " + ex.getResponseBodyAsString(),
                    ex
            );
        }
    }

    public KakaoUserResponse getUserInfo(String kakaoAccessToken) {
        if (!StringUtils.hasText(kakaoAccessToken)) {
            throw new ResponseStatusException(BAD_REQUEST, "카카오 access_token이 필요합니다.");
        }
        if (!StringUtils.hasText(kakaoProperties.userInfoUri())) {
            throw new ResponseStatusException(BAD_GATEWAY, "Kakao user-info URI가 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(kakaoAccessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            log.info("Fetching Kakao user info with access token");
            ResponseEntity<KakaoUserResponse> response = restTemplate.exchange(
                    kakaoProperties.userInfoUri(),
                    HttpMethod.GET,
                    requestEntity,
                    KakaoUserResponse.class
            );

            KakaoUserResponse body = response.getBody();
            if (body == null || body.id() == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "카카오 사용자 정보 응답이 비어 있습니다.");
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            log.error("Failed to fetch Kakao user info. Status: {}, Response: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "카카오 사용자 정보 조회에 실패했습니다: " + ex.getResponseBodyAsString(),
                    ex
            );
        }
    }
}
