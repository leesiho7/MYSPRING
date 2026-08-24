package com.tem.spring.core.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 일일 API 호출 제한 (Rate Limiting) 관리 서비스
 * IP / 회원별 일일 최대 호출 횟수 제어 및 429 방어
 */
@Slf4j
@Service
public class RateLimitService {

    private final boolean enabled;
    private final int dailyLimit;

    public RateLimitService(
            @Value("${trading.rate-limit.enabled:true}") String enabledStr,
            @Value("${trading.rate-limit.daily-limit:1000}") String dailyLimitStr) {
        boolean parsedEnabled = true;
        if (enabledStr != null && !enabledStr.isBlank()) {
            parsedEnabled = Boolean.parseBoolean(enabledStr.trim());
        }
        this.enabled = parsedEnabled;

        int parsedLimit = 1000;
        if (dailyLimitStr != null && !dailyLimitStr.isBlank()) {
            try {
                parsedLimit = Integer.parseInt(dailyLimitStr.trim());
            } catch (Exception ignored) {}
        }
        this.dailyLimit = parsedLimit;
        log.info("[RateLimitService] Initialized (enabled: {}, dailyLimit: {})", this.enabled, this.dailyLimit);
    }

    // key: "IP_or_USER:YYYY-MM-DD", value: requestCount
    private final Map<String, AtomicInteger> dailyRequestCounts = new ConcurrentHashMap<>();
    private volatile LocalDate lastCleanedDate = LocalDate.now();

    public boolean tryConsume(String clientId) {
        if (!enabled) {
            return true;
        }

        cleanOldDateKeys();

        LocalDate today = LocalDate.now();
        String key = clientId + ":" + today;

        AtomicInteger counter = dailyRequestCounts.computeIfAbsent(key, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();

        if (currentCount > dailyLimit) {
            log.warn("[RateLimit] Client '{}' exceeded daily quota: {}/{}", clientId, currentCount, dailyLimit);
            return false;
        }

        log.info("[RateLimit] Client '{}' quota: {}/{} remaining: {}", clientId, currentCount, dailyLimit, dailyLimit - currentCount);
        return true;
    }

    public int getRemainingQuota(String clientId) {
        if (!enabled) return 999;
        String key = clientId + ":" + LocalDate.now();
        AtomicInteger counter = dailyRequestCounts.get(key);
        int used = counter != null ? counter.get() : 0;
        return Math.max(0, dailyLimit - used);
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    private void cleanOldDateKeys() {
        LocalDate today = LocalDate.now();
        if (!today.equals(lastCleanedDate)) {
            dailyRequestCounts.entrySet().removeIf(entry -> !entry.getKey().endsWith(":" + today));
            lastCleanedDate = today;
            log.info("[RateLimit] Daily request counters reset for new day: {}", today);
        }
    }
}
