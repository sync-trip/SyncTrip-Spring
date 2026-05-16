package com.sync.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JWT 토큰에서 추출한 현재 로그인한 사용자 ID를 컨트롤러 메서드에 주입하는 어노테이션
 *
 * 사용 예:
 * @PostMapping
 * public ResponseEntity<BandResponse> createBand(
 *     @LoginUser Long userId,
 *     @RequestBody BandCreateRequest request
 * ) { ... }
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
    /**
     * 아이디 필드명 (기본값: "id")
     * JWT 토큰의 subject에서 추출됨
     */
    String value() default "";
}

