package com.sync.service.jwt;

import com.sync.config.JwtProperties;
import com.sync.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public TokenPair issueTokenPair(User user) {
        // 로그인 성공 시 access/refresh 토큰을 한 번에 발급
        String accessToken = createToken(user, "access", jwtProperties.accessTokenTtlSeconds());
        String refreshToken = createToken(user, "refresh", jwtProperties.refreshTokenTtlSeconds());
        return new TokenPair(accessToken, refreshToken,
                jwtProperties.accessTokenTtlSeconds(), jwtProperties.refreshTokenTtlSeconds());
    }

    public Jws<Claims> parse(String token) {
        // 토큰 서명/만료를 검증하고 유효하면 클레임을 반환
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(UNAUTHORIZED, "토큰이 비어 있습니다.");
        }
        try {
            return Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secretBytes()))
                    .build()
                    .parseSignedClaims(token);
        } catch (Exception ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "유효하지 않은 토큰입니다.", ex);
        }
    }

    private String createToken(User user, String type, long ttlSeconds) {
        // subject에는 내부 사용자 ID를 넣고, type(access/refresh)로 용도를 구분
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("type", type)
                .claim("provider", user.getOauthProvider().name())
                .signWith(Keys.hmacShaKeyFor(secretBytes()))
                .compact();
    }

    private byte[] secretBytes() {
        // HS256 서명 키는 최소 32바이트 이상이어야 안전하게 동작
        String secret = jwtProperties.secret();
        if (!StringUtils.hasText(secret)) {
            throw new ResponseStatusException(BAD_GATEWAY, "JWT secret이 설정되지 않았습니다.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new ResponseStatusException(BAD_GATEWAY, "JWT secret은 최소 32바이트 이상이어야 합니다.");
        }
        return bytes;
    }

    public record TokenPair(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresIn,
            long refreshTokenExpiresIn
    ) {
    }
}

