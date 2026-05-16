package com.sync.common.security;

import com.sync.common.annotation.LoginUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @LoginUser 어노테이션이 붙은 핸들러 메서드 파라미터에 현재 로그인한 사용자 ID를 주입
 *
 * 동작:
 * 1. SecurityContextHolder에서 현재 인증 정보 꺼냄
 * 2. principal에 저장된 userId (문자열) → Long으로 변환
 * 3. 메서드 파라미터에 주입
 *
 * 사용 예:
 * @PostMapping
 * public ResponseEntity<BandResponse> createBand(
 *     @LoginUser Long userId
 * ) { ... }
 */
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

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
        // SecurityContextHolder에서 인증 정보 추출
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("인증되지 않은 요청입니다.");
        }

        // principal에 저장된 userId (문자열) → Long으로 변환
        String userIdStr = (String) authentication.getPrincipal();
        return Long.parseLong(userIdStr);
    }
}

