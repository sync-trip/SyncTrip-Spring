package com.sync.common.security;

import com.sync.common.annotation.LoginUser;
import com.sync.service.jwt.JwtTokenProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * @LoginUser 어노테이션이 붙은 핸들러 메서드 파라미터에 현재 로그인한 사용자 ID를 주입
 *
 * 동작:
 * 1. 보안 활성화 시: SecurityContextHolder에서 현재 인증 정보 꺼냄
 * 2. 보안 비활성화 시:
 *    - 1순위: URL 쿼리 파라미터 (?userId=...)
 *    - 2순위: Authorization 헤더 (JWT 토큰)
 * 3. 메서드 파라미터에 주입
 */
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtTokenProvider jwtTokenProvider;
    private final boolean securityEnabled;

    public LoginUserArgumentResolver(JwtTokenProvider jwtTokenProvider, boolean securityEnabled) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityEnabled = securityEnabled;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) throws Exception {
        // [1] 보안이 활성화된 경우 (운영 환경)
        if (securityEnabled) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증되지 않은 요청입니다.");
            }

            Object principal = authentication.getPrincipal();
            if (!(principal instanceof String userIdStr)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다.");
            }
            try {
                return Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자 ID입니다.");
            }
        }

        // [2] 보안이 비활성화된 경우 (개발 환경 편의 기능)

        // 2-1. URL 파라미터 확인 (Postman 테스트용: ?userId=1)
        String userIdParam = webRequest.getParameter("userId");
        if (userIdParam != null && !userIdParam.isBlank()) {
            try {
                return Long.parseLong(userIdParam);
            } catch (NumberFormatException e) {
                // 숫자가 아니면 무시하고 다음 단계로
            }
        }

        // 2-2. Authorization 헤더 확인 (에뮬레이터 테스트용)
        String authHeader = webRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                return jwtTokenProvider.getUserIdFromToken(token);
            } catch (Exception e) {
                // 토큰이 유효하지 않으면 무시 (보안 비활성화 모드이므로)
            }
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다. (?userId=파라미터 또는 올바른 Bearer 토큰이 필요합니다)");
    }
}

