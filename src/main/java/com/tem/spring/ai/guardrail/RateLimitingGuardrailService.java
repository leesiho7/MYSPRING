package com.tem.spring.ai.guardrail;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [Rule 1. Rate Limiting & Cost Guardrail (비용 및 DoS 공격 방지)]
 * Bucket4j 토큰 버킷을 활용하여 IP/유저 ID별로 
 * 1분당 허용 요청 수를 하드웨어/코드 레벨에서 엄격히 제한합니다.
 */
@Slf4j
@Service
public class RateLimitingGuardrailService {

    // 클라이언트 IP/ID별 버킷 저장소
    private final Map<String, Bucket> visionBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> chatBuckets = new ConcurrentHashMap<>();

    // 1분당 최대 5회 Vision 분석 허용
    private static final int VISION_MAX_PER_MINUTE = 5;
    // 1분당 최대 15회 Chat 리서치 허용
    private static final int CHAT_MAX_PER_MINUTE = 15;

    /**
     * Vision AI 차트 분석 레이트 리미트 검증 (1분당 5회)
     * @return true: 허용, false: 초과 (차단)
     */
    public boolean tryConsumeVision(String clientKey) {
        String key = (clientKey != null && !clientKey.isBlank()) ? clientKey : "ANONYMOUS";
        Bucket bucket = visionBuckets.computeIfAbsent(key, k -> createBucket(VISION_MAX_PER_MINUTE, Duration.ofMinutes(1)));
        boolean consumed = bucket.tryConsume(1);
        if (!consumed) {
            log.warn("[RateLimitingGuardrail] 🛑 Rate limit exceeded for VISION analysis by client: {} (Max: {}/min)", key, VISION_MAX_PER_MINUTE);
        }
        return consumed;
    }

    /**
     * AI Research Chat 레이트 리미트 검증 (1분당 15회)
     */
    public boolean tryConsumeChat(String clientKey) {
        String key = (clientKey != null && !clientKey.isBlank()) ? clientKey : "ANONYMOUS";
        Bucket bucket = chatBuckets.computeIfAbsent(key, k -> createBucket(CHAT_MAX_PER_MINUTE, Duration.ofMinutes(1)));
        boolean consumed = bucket.tryConsume(1);
        if (!consumed) {
            log.warn("[RateLimitingGuardrail] 🛑 Rate limit exceeded for CHAT research by client: {} (Max: {}/min)", key, CHAT_MAX_PER_MINUTE);
        }
        return consumed;
    }

    private Bucket createBucket(int capacity, Duration refillPeriod) {
        Refill refill = Refill.greedy(capacity, refillPeriod);
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket.builder().addLimit(limit).build();
    }
}
