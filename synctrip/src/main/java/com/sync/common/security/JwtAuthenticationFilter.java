package com.sync.common.security;

import com.sync.service.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT Bearer 토큰을 검증하고 SecurityContext에 인증 정보를 설정하는 필터
 *
 * 요청 흐름:
 * 1. Authorization 헤더에서 "Bearer {token}" 형식의 토큰 추출
 * 2. JwtTokenProvider로 토큰 검증 (서명, 만료시간)
 * 3. 토큰에서 userId(subject) 추출
 * 4. SecurityContextHolder에 Authentication 설정 (다운스트림에서 사용 가능)
 * 5. 예외 발생 시 필터링 계속 진행 (401 응답은 컨트롤러/핸들러에서 처리)
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // Authorization 헤더에서 토큰 추출
            String token = extractToken(request);

            if (token != null) {
                // JWT 검증 및 userId 추출
                Jws<Claims> jws = jwtTokenProvider.parse(token);
                String userId = jws.getPayload().getSubject(); // subject = "userId"
                String tokenType = jws.getPayload().get("type", String.class);

                // access 토큰일 때만 인증 설정 (refresh 토큰은 무시)
                if ("access".equals(tokenType)) {
                    // SecurityContext에 인증 정보 설정
                    // principal: userId (문자열)
                    // credentials: null
                    // authorities: 빈 배열 (역할이 필요하면 여기에 추가)
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    java.util.Collections.emptyList()
                            );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    logger.debug("JWT 인증 성공: userId={}", userId);
                } else {
                    logger.debug("Refresh 토큰은 인증 처리하지 않음");
                }
            }
        } catch (Exception ex) {
            // JWT 파싱 실패 시 로그 기록하고 필터링 계속 진행
            // (401 오류는 @LoginUser 어노테이션이나 AbstractAuthenticationProcessingFilter에서 처리)
            logger.debug("JWT 검증 실패: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더에서 Bearer 토큰 추출
     * 형식: "Bearer {token}"
     *
     * @return 토큰 문자열 (없으면 null)
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

