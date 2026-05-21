package com.sync.service;

import com.sync.domain.user.OauthProvider;
import com.sync.domain.user.User;
import com.sync.dto.auth.LoginResponse;
import com.sync.dto.google.GoogleUserResponse;
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
    private final GoogleAuthService googleAuthService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(KakaoAuthService kakaoAuthService, GoogleAuthService googleAuthService, UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.kakaoAuthService = kakaoAuthService;
        this.googleAuthService = googleAuthService;
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

    /**
     * 로그아웃 처리
     * JWT는 서버에서 관리하지 않는 Stateless 방식이므로,
     * 백엔드에서는 특별한 처리가 없고 프론트엔드에서 로컬 토큰을 삭제하면 된다.
     * (향후 토큰 블랙리스트 필요 시 확장)
     */
    public void logout(Long userId) {
        // 현재: Stateless JWT이므로 백엔드에서 할 일 없음
        // 향후: refresh token 블랙리스트 DB 저장 등 추가 가능
        // → userId는 로그 기록이나 감시 목적으로 사용 가능
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
