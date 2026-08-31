package com.tem.spring.ai.guardrail;

import com.tem.spring.ai.entity.UserQueryEntity;
import com.tem.spring.ai.repository.UserQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * [Rule 5. AI 추론 트레이스 및 감사 로그 (Audit Trail / Observability)]
 * 모든 AI 추론/리서치 질의 시 [요청 시각 | Symbol | RAG Document ID 목록 |
 * FastDTW Top 3 Match Score & 승률 | Qwen Prompt 원본 | AI Response | 앙상블 판정]을
 * 비동기(@Async)로 DB에 저장하여 완벽한 감사 추적성을 보장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditObservabilityService {

    private final ObjectProvider<UserQueryRepository> userQueryRepositoryProvider;

    @Async
    public void recordAuditTrailAsync(String conversationId, String userId, String symbol,
                                      String rawPrompt, String sanitizedPrompt, String llmResponse,
                                      String intentVerdict, Integer qualityScore, String ragContext,
                                      String documentIds, Double fastDtwScore, Double fastDtwWinRate,
                                      String ensembleVerdict, String gateRationale,
                                      long responseTimeMs, boolean isFallback) {
        try {
            UserQueryRepository repo = userQueryRepositoryProvider.getIfAvailable();
            if (repo == null) {
                log.debug("[AiAuditObservability] UserQueryRepository not available, skipping audit persist.");
                return;
            }

            UserQueryEntity entity = UserQueryEntity.builder()
                    .conversationId(conversationId)
                    .userId(userId != null ? userId : "ANONYMOUS_USER")
                    .symbol(symbol != null ? symbol.toUpperCase() : "UNKNOWN")
                    .prompt(rawPrompt)
                    .llmResponse(llmResponse)
                    .intentVerdict(intentVerdict)
                    .entryQualityScore(qualityScore)
                    .ragContext(ragContext)
                    .documentIds(documentIds)
                    .fastDtwMatchScore(fastDtwScore)
                    .fastDtwWinRate(fastDtwWinRate)
                    .ensembleVerdict(ensembleVerdict)
                    .gateRationale(gateRationale)
                    .responseTimeMs(responseTimeMs)
                    .isFallback(isFallback)
                    .createdAt(LocalDateTime.now())
                    .build();

            repo.save(entity);
            log.info("[AiAuditObservability] 💾 [Audit Trail Logged] ConvID: {}, Symbol: {}, EnsembleVerdict: {}, DtwWinRate: {}%, Time: {}ms",
                    conversationId, symbol, ensembleVerdict,
                    fastDtwWinRate != null ? Math.round(fastDtwWinRate * 100.0) : 0, responseTimeMs);

        } catch (Exception e) {
            log.warn("[AiAuditObservability] ⚠️ Failed to persist async audit trail: {}", e.getMessage());
        }
    }
}
