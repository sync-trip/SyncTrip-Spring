package com.sync.service;

import com.sync.domain.user.User;
import com.sync.dto.auth.LoginResponse;
import com.sync.dto.kakao.KakaoUserResponse;
import com.sync.repository.UserRepository;
import com.sync.service.jwt.JwtTokenProvider;
import com.sync.service.jwt.JwtTokenProvider.TokenPair;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private KakaoAuthService kakaoAuthService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginWithKakaoAccessToken_createsUserAndIssuesTokens() {
        KakaoUserResponse kakaoUser = new KakaoUserResponse(
                555L,
                new KakaoUserResponse.KakaoAccount(
                        "new-user@example.com",
                        new KakaoUserResponse.Profile("new-user", "https://image.example/profile.png")
                )
        );
        User savedUser = User.kakaoUser("new-user@example.com", "new-user", "https://image.example/profile.png", "555");
        setId(savedUser, 1L);

        when(kakaoAuthService.getUserInfo("kakao-access-token")).thenReturn(kakaoUser);
        when(userRepository.findByOauthProviderAndOauthIdAndIsDeletedFalse(any(), eq("555"))).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.issueTokenPair(savedUser)).thenReturn(new TokenPair("a-token", "r-token", 900L, 1209600L));

        LoginResponse response = authService.loginWithKakaoAccessToken("kakao-access-token");

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.newUser()).isTrue();
        assertThat(response.accessToken()).isEqualTo("a-token");
        assertThat(response.refreshToken()).isEqualTo("r-token");
    }

    private void setId(User user, long id) {
        try {
            java.lang.reflect.Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

