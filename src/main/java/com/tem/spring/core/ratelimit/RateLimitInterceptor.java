package com.tem.spring.core.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 트레이딩 및 AI 분석 API 전용 Rate Limiting 인터셉터
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientIp = extractClientIp(request);
        String userHeader = request.getHeader("X-User-Id");
        String clientId = (userHeader != null && !userHeader.isBlank()) ? "USER_" + userHeader : "IP_" + clientIp;

        int limit = rateLimitService.getDailyLimit();
        int remaining = rateLimitService.getRemainingQuota(clientId);

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining - 1)));

        boolean allowed = rateLimitService.tryConsume(clientId);
        if (!allowed) {
            throw new RateLimitExceededException(
                    String.format("오늘 일일 무료 AI 정밀 분석 횟수(%d회)를 모두 소진하셨습니다. (내일 자정에 갱신됩니다)", limit),
                    limit,
                    0
            );
        }

        return true;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
