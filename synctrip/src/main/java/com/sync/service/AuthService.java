package com.sync.service;

import com.sync.domain.user.OauthProvider;
import com.sync.domain.user.User;
import com.sync.dto.auth.LoginResponse;
import com.sync.dto.kakao.KakaoUserResponse;
import com.sync.repository.UserRepository;
import com.sync.service.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final KakaoAuthService kakaoAuthService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(KakaoAuthService kakaoAuthService, UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.kakaoAuthService = kakaoAuthService;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public LoginResponse loginWithKakaoAccessToken(String kakaoAccessToken) {
        // 카카오 access token으로 사용자 기본 프로필 조회
        KakaoUserResponse kakaoUser = kakaoAuthService.getUserInfo(kakaoAccessToken);
        String oauthId = String.valueOf(kakaoUser.id());

        KakaoUserResponse.KakaoAccount kakaoAccount = kakaoUser.kakaoAccount();
        String email = kakaoAccount == null ? null : kakaoAccount.email();
        String nickname = extractNickname(kakaoUser);
        String profileImageUrl = extractProfileImageUrl(kakaoUser);

        Optional<User> existingUser = userRepository.findByOauthProviderAndOauthIdAndIsDeletedFalse(OauthProvider.KAKAO, oauthId);
        boolean newUser = existingUser.isEmpty();

        // 기존 회원이면 프로필 갱신, 없으면 신규 회원으로 생성
        User user = existingUser
                .map(found -> {
                    found.updateProfile(nickname, profileImageUrl, email);
                    return found;
                })
                .orElseGet(() -> userRepository.save(User.kakaoUser(email, nickname, profileImageUrl, oauthId)));

        JwtTokenProvider.TokenPair tokenPair = jwtTokenProvider.issueTokenPair(user);
        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProfileImageUrl(),
                newUser,
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.accessTokenExpiresIn(),
                tokenPair.refreshTokenExpiresIn()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshToken) {
        // refresh token 서명/만료를 검증하고 클레임을 읽는다
        Jws<Claims> claimsJws = jwtTokenProvider.parse(refreshToken);
        Claims claims = claimsJws.getPayload();

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new ResponseStatusException(UNAUTHORIZED, "refresh token이 아닙니다.");
        }

        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "토큰 subject 형식이 올바르지 않습니다.", ex);
        }

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "존재하지 않는 사용자입니다."));

        // 유효한 사용자면 access/refresh 토큰을 새로 발급
        JwtTokenProvider.TokenPair tokenPair = jwtTokenProvider.issueTokenPair(user);
        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProfileImageUrl(),
                false,
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.accessTokenExpiresIn(),
                tokenPair.refreshTokenExpiresIn()
        );
    }

    private String extractNickname(KakaoUserResponse response) {
        if (response.kakaoAccount() != null
                && response.kakaoAccount().profile() != null
                && StringUtils.hasText(response.kakaoAccount().profile().nickname())) {
            return response.kakaoAccount().profile().nickname();
        }
        return "kakao_" + response.id();
    }

    private String extractProfileImageUrl(KakaoUserResponse response) {
        if (response.kakaoAccount() == null || response.kakaoAccount().profile() == null) {
            return null;
        }
        return response.kakaoAccount().profile().profileImageUrl();
    }
}
