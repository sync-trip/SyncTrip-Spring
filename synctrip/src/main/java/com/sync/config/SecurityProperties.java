package com.sync.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플리케이션 보안 설정 프로퍼티
 *
 * application.yml에서:
 * app:
 *   security:
 *     enabled: true/false
 *     public-paths:
 *       - /api/auth/**
 *       - /debug/**
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    boolean enabled,
    List<String> publicPaths
) {
    // 기본값
    public SecurityProperties {
        if (publicPaths == null) {
            publicPaths = List.of();
        }
    }
}

