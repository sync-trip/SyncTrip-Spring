package com.sync.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 전역 CORS 설정
 * - 브라우저에서 다른 오리진(도메인)에서 API 호출할 때 발생하는 CORS 오류를 해결합니다.
 * - 허용할 오리진 목록은 환경변수/프로퍼티 `CORS_ALLOWED_ORIGINS`로 주입합니다(콤마로 구분).
 *
 * 사용 예:
 * - 로컬 개발: CORS_ALLOWED_ORIGINS=http://localhost:8080
 * - 테스트/운영: CORS_ALLOWED_ORIGINS=https://test.sync-trip.app,https://api.sync-trip.app
 *
 * 주의:
 * - 운영에서는 와일드카드(*) 사용을 피하고 가능한 한 정확한 도메인만 허용하세요.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // 환경변수 또는 프로퍼티에서 읽음(기본값은 로컬 호스트만 허용)
    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:8080}")
    private String allowedOriginsProperty;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 콤마로 구분된 리스트를 파싱
        List<String> allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // 모든 API 경로에 대해 CORS 설정 적용
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

