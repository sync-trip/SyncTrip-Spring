package com.sync.service;

import com.sync.domain.user.OauthProvider;
import com.sync.domain.user.User;
import com.sync.dto.auth.LoginResponse;
import com.sync.dto.google.GoogleUserResponse;
import com.sync.dto.kakao.KakaoUserResponse;
import com.sync.repository.UserRepository;
import com.sync.service.jwt.JwtTokenProvider;
import com.sync.service.jwt.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final KakaoAuthService kakaoAuthService;
    private final GoogleAuthService googleAuthService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthService(KakaoAuthService kakaoAuthService, GoogleAuthService googleAuthService,
                       UserRepository userRepository, JwtTokenProvider jwtTokenProvider,
                       TokenBlacklistService tokenBlacklistService) {
        this.kakaoAuthService = kakaoAuthService;
        this.googleAuthService = googleAuthService;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
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

    @Transactional
    public LoginResponse loginWithGoogleIdToken(String idToken) {
        // Google ID Token 검증 및 사용자 정보 추출
        GoogleUserResponse googleUser = googleAuthService.validateAndGetUserInfo(idToken);
        String oauthId = googleUser.sub();

        String email = googleUser.email();
        String nickname = StringUtils.hasText(googleUser.name()) ? googleUser.name() : ("google_" + oauthId);
        String profileImageUrl = googleUser.picture();

        Optional<User> existingUser = userRepository.findByOauthProviderAndOauthIdAndIsDeletedFalse(OauthProvider.GOOGLE, oauthId);
        boolean newUser = existingUser.isEmpty();

        // 기존 회원이면 프로필 갱신, 없으면 신규 회원으로 생성
        User user = existingUser
                .map(found -> {
                    found.updateProfile(nickname, profileImageUrl, email);
                    return found;
                })
                .orElseGet(() -> userRepository.save(User.googleUser(email, nickname, profileImageUrl, oauthId)));

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
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw new ResponseStatusException(UNAUTHORIZED, "로그아웃된 토큰입니다.");
        }
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

    public void logout(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) return;
        try {
            Jws<Claims> jws = jwtTokenProvider.parse(refreshToken);
            if (!"refresh".equals(jws.getPayload().get("type", String.class))) return;
            Date expiry = jws.getPayload().getExpiration();
            long remainingSeconds = (expiry.getTime() - Instant.now().toEpochMilli()) / 1000;
            if (remainingSeconds > 0) {
                tokenBlacklistService.add(refreshToken, remainingSeconds);
            }
        } catch (Exception ignored) {
            // 만료되었거나 유효하지 않은 토큰은 무시 (이미 무효)
        }
    }

    /**
     * 회원탈퇴 (Soft Delete)
     * - 사용자 계정을 논리 삭제 (is_deleted = true)
     * - 기존 데이터는 보존
     */
    @Transactional
    public void withdrawUser(Long userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "사용자를 찾을 수 없습니다."
                ));

        // Soft Delete: isDeleted = true로 설정
        user.withdraw();
        userRepository.save(user);
    }
}
