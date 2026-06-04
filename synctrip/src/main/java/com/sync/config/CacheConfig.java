package com.sync.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Redis 캐시 설정
 * - 캐시별 TTL을 커스터마이즈한다.
 * - RedisCacheManagerBuilderCustomizer를 사용하면 Spring Boot가 자동 구성한 RedisCacheManager를
 *   그대로 유지하면서 특정 캐시의 설정만 덮어쓸 수 있다.
 *   (전체 RedisCacheManager를 재정의하지 않으므로 destination-search·holidays 등 기존 캐시는 영향 없음)
 */
@Configuration
public class CacheConfig {

    // 장소 검색 결과 캐시 TTL — 시연 세션 내내 유지되면서 평점/영업시간 변동도 하루 안에 반영되도록 6시간
    private static final Duration PLACE_SEARCH_TTL = Duration.ofHours(6);

    @Bean
    public RedisCacheManagerBuilderCustomizer placeSearchCacheCustomizer() {
        return builder -> builder.withCacheConfiguration(
                "place-search",
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(PLACE_SEARCH_TTL));
    }
}
