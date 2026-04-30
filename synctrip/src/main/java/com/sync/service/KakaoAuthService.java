package com.sync.service;

import com.sync.config.KakaoProperties;
import com.sync.dto.kakao.KakaoTokenResponse;
import com.sync.dto.kakao.KakaoUserResponse;
import java.util.List;
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

    private final RestTemplate restTemplate;
    private final KakaoProperties kakaoProperties;

    public KakaoAuthService(RestTemplate restTemplate, KakaoProperties kakaoProperties) {
        this.restTemplate = restTemplate;
        this.kakaoProperties = kakaoProperties;
    }

    public KakaoTokenResponse exchangeToken(String code, String state) {
        // 토큰 교환에 필요한 필수 파라미터/설정값을 먼저 검증
        if (!StringUtils.hasText(code)) {
            throw new ResponseStatusException(BAD_REQUEST, "카카오 인가 코드(code)가 필요합니다.");
        }
        if (!StringUtils.hasText(kakaoProperties.tokenUri())) {
            throw new ResponseStatusException(BAD_GATEWAY, "Kakao token URI가 설정되지 않았습니다.");
        }
        if (!StringUtils.hasText(kakaoProperties.clientId())) {
            throw new ResponseStatusException(BAD_GATEWAY, "Kakao client-id가 설정되지 않았습니다.");
        }
        if (!StringUtils.hasText(kakaoProperties.redirectUri())) {
            throw new ResponseStatusException(BAD_GATEWAY, "Kakao redirect-uri가 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // 카카오 OAuth2 토큰 교환 규격에 맞는 form 데이터 구성
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
            // 인가 코드를 access/refresh 토큰으로 교환
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
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "카카오 토큰 발급에 실패했습니다: " + ex.getResponseBodyAsString(),
                    ex
            );
        }
    }

    public KakaoUserResponse getUserInfo(String kakaoAccessToken) {
        // 사용자 정보 조회에 필요한 access token/설정값 검증
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
            // Bearer 토큰으로 카카오 사용자 정보 API 호출
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
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "카카오 사용자 정보 조회에 실패했습니다: " + ex.getResponseBodyAsString(),
                    ex
            );
        }
    }
}
