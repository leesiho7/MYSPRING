package com.tem.spring.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * [서킷 브레이커 & API Caching (비용 및 API 쿼타 보호)]
 * 동일한 종목(BTCUSDT, 005930 등)에 대해 3~5분 이내 재요청 시
 * 고비용 LLM/Vision API를 중복 호출하지 않고 캐시된 리포트를 즉시 반환
 */
@Configuration
public class CacheConfig {

    public static final String AI_RESEARCH_CACHE = "aiResearchCache";
    public static final String VISION_ANALYSIS_CACHE = "visionAnalysisCache";
    public static final String NEWS_SCRAPE_CACHE = "newsScrapeCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                AI_RESEARCH_CACHE,
                VISION_ANALYSIS_CACHE,
                NEWS_SCRAPE_CACHE
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(3, TimeUnit.MINUTES) // 3분 스마트 캐시 TTL
                .recordStats()
        );
        return cacheManager;
    }
}
