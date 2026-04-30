package com.sync.service;

import com.sync.config.KakaoProperties;
import com.sync.dto.kakao.KakaoTokenResponse;
import com.sync.dto.kakao.KakaoUserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoAuthServiceTest {

    @Test
    void exchangeToken_returnsKakaoTokenResponse() {
        RestTemplate restTemplate = new RestTemplate();
        KakaoProperties properties = new KakaoProperties(
                "https://kauth.kakao.com/oauth/authorize",
                "https://kapi.kakao.com/oauth/token",
                "https://kapi.kakao.com/v2/user/me",
                "client-id",
                "client-secret",
                "http://localhost:8080/auth/kakao/callback",
                "account_email,profile_nickname,profile_image"
        );

        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://kapi.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=authorization_code"),
                        org.hamcrest.Matchers.containsString("client_id=client-id"),
                        org.hamcrest.Matchers.containsString("client_secret=client-secret"),
                        org.hamcrest.Matchers.containsString("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fauth%2Fkakao%2Fcallback"),
                        org.hamcrest.Matchers.containsString("code=auth-code-123")
                )))
                .andRespond(withSuccess("""
                        {
                          "token_type": "bearer",
                          "access_token": "access-token-value",
                          "refresh_token": "refresh-token-value",
                          "expires_in": 21599,
                          "refresh_token_expires_in": 5183999,
                          "scope": "account_email profile_nickname profile_image"
                        }
                        """, MediaType.APPLICATION_JSON));

        KakaoAuthService service = new KakaoAuthService(restTemplate, properties);
        KakaoTokenResponse response = service.exchangeToken("auth-code-123", null);

        assertThat(response.accessToken()).isEqualTo("access-token-value");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-value");
        assertThat(response.tokenType()).isEqualTo("bearer");
        assertThat(response.expiresIn()).isEqualTo(21599L);

        server.verify();
    }

    @Test
    void getUserInfo_returnsKakaoProfile() {
        RestTemplate restTemplate = new RestTemplate();
        KakaoProperties properties = new KakaoProperties(
                "https://kauth.kakao.com/oauth/authorize",
                "https://kapi.kakao.com/oauth/token",
                "https://kapi.kakao.com/v2/user/me",
                "client-id",
                "client-secret",
                "http://localhost:8080/auth/kakao/callback",
                "account_email,profile_nickname,profile_image"
        );

        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access-token"))
                .andRespond(withSuccess("""
                        {
                          "id": 123456789,
                          "kakao_account": {
                            "email": "user@example.com",
                            "profile": {
                              "nickname": "sync-user",
                              "profile_image_url": "https://k.kakaocdn.net/profile.jpg"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        KakaoAuthService service = new KakaoAuthService(restTemplate, properties);
        KakaoUserResponse response = service.getUserInfo("kakao-access-token");

        assertThat(response.id()).isEqualTo(123456789L);
        assertThat(response.kakaoAccount().email()).isEqualTo("user@example.com");
        assertThat(response.kakaoAccount().profile().nickname()).isEqualTo("sync-user");

        server.verify();
    }
}
