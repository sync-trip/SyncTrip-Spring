package com.sync.config;

import com.sync.common.security.JwtAuthenticationFilter;
import com.sync.common.security.LoginUserArgumentResolver;
import com.sync.service.jwt.JwtTokenProvider;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Security 및 JWT 인증 설정
 *
 * 환경별 설정:
 * - 개발(dev): 모든 엔드포인트 공개 (app.security.enabled=false)
 * - 운영(prod): JWT 인증 필수 (app.security.enabled=true)
 *
 * 사용:
 * - 로컬 개발: ./gradlew bootRun (dev 프로필, 보안 비활성화)
 * - 배포 테스트: APP_SECURITY_ENABLED=true ./gradlew bootRun
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityProperties securityProperties;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider, SecurityProperties securityProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityProperties = securityProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 개발 환경: 모든 엔드포인트 공개 (인증 무시)
        if (!securityProperties.enabled()) {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
            return http.build();
        }

        // 운영 환경: JWT 인증 필수
        http
                // CSRF 비활성화 (JWT 사용 또는 Stateless API이므로 불필요)
                .csrf(csrf -> csrf.disable())

                // 세션 미사용 (Stateless 인증)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 엔드포인트별 접근 제어
                .authorizeHttpRequests(authz -> authz
                        // 공개 엔드포인트 (인증 불필요)
                        .requestMatchers(securityProperties.publicPaths().toArray(new String[0]))
                        .permitAll()

                        // 나머지는 모두 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 인증 필터 등록 (UsernamePasswordAuthenticationFilter 직전)
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * @LoginUser 어노테이션 처리용 ArgumentResolver 등록
     * 개발 환경에서도 에뮬레이터/Postman 편의를 위해 항상 등록하되, 내부에서 보안 설정에 따라 다르게 동작함
     */
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new LoginUserArgumentResolver(jwtTokenProvider, securityProperties.enabled()));
            }
        };
    }
}



