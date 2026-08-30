package com.tem.spring.controller;

import com.tem.spring.ai.service.OllamaMarketAgentService;
import com.tem.spring.core.model.*;
import com.tem.spring.decision.SignalAggregatorService;
import com.tem.spring.ingestion.service.MarketDataIngestionService;
import com.tem.spring.quant.adapter.BarSeriesMapper;
import com.tem.spring.quant.indicator.TechnicalIndicatorEngine;
import com.tem.spring.quant.strategy.BacktestingEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ta4j.core.BarSeries;

import java.util.List;

/**
 * 트레이딩 인텔리전스 시스템 REST API 엔드포인트
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TradingSystemController {

    private final MarketDataIngestionService ingestionService;
    private final BarSeriesMapper barSeriesMapper;
    private final TechnicalIndicatorEngine indicatorEngine;
    private final BacktestingEngine backtestingEngine;
    private final OllamaMarketAgentService ollamaService;
    private final SignalAggregatorService aggregatorService;
    private final com.tem.spring.ai.service.AiResearchChatService researchChatService;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.ai.vectorstore.VectorStore> vectorStoreProvider;
    private final org.springframework.beans.factory.ObjectProvider<com.tem.spring.ai.repository.UserQueryRepository> userQueryRepositoryProvider;
    private final org.springframework.beans.factory.ObjectProvider<com.tem.spring.ai.service.ProactiveNewsWarmupBatchService> warmupBatchServiceProvider;
    private final org.springframework.beans.factory.ObjectProvider<com.tem.spring.ai.service.BrightDataNewsScraperService> brightDataNewsScraperServiceProvider;
    private final com.tem.spring.gamification.service.StreakRewardClaimService streakRewardClaimService;

    /**
     * 1. OpenBB 스타일 데이터 수집 조회 API
     */
    @GetMapping("/market/historical")
    public ResponseEntity<List<Candle>> getHistoricalData(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "D1") TimeFrame timeFrame,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ingestionService.getHistoricalData(symbol, timeFrame, limit));
    }

    /**
     * 2. ta4j 기술적 지표 계산 API
     */
    @GetMapping("/quant/indicators")
    public ResponseEntity<QuantitativeSignal> getIndicators(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "D1") TimeFrame timeFrame,
            @RequestParam(defaultValue = "100") int limit) {
        List<Candle> candles = ingestionService.getHistoricalData(symbol, timeFrame, limit);
        BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);
        return ResponseEntity.ok(indicatorEngine.calculateSignals(series));
    }

    /**
     * 3. ta4j 기본 전략 백테스팅 실행 API
     */
    @GetMapping("/quant/backtest")
    public ResponseEntity<BacktestResult> runBacktest(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "D1") TimeFrame timeFrame,
            @RequestParam(defaultValue = "200") int limit) {
        List<Candle> candles = ingestionService.getHistoricalData(symbol, timeFrame, limit);
        BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);
        return ResponseEntity.ok(backtestingEngine.runRsiSmaStrategy(series));
    }

    /**
     * 3-1. [레고 블록] 사용자 커스텀 퀀트 전략 백테스팅 실행 API
     */
    @PostMapping("/quant/custom-backtest")
    public ResponseEntity<BacktestResult> runCustomBacktest(
            @RequestBody com.tem.spring.quant.dto.CustomStrategyRequest request) {
        String symbol = request.getSymbol() != null ? request.getSymbol() : "BTCUSDT";
        TimeFrame timeFrame = request.getTimeFrame() != null ? request.getTimeFrame() : TimeFrame.D1;
        int limit = request.getLimit() > 0 ? request.getLimit() : 200;

        List<Candle> candles = ingestionService.getHistoricalData(symbol, timeFrame, limit);
        BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);
        return ResponseEntity.ok(backtestingEngine.runCustomStrategy(series, request));
    }

    /**
     * 4. Spring AI + Ollama 뉴스/공시 감성 분석 API
     */
    @GetMapping("/ai/sentiment")
    public ResponseEntity<QualitativeInsight> getMarketSentiment(
            @RequestParam(defaultValue = "BTCUSDT") String symbol) {
        return ResponseEntity.ok(ollamaService.analyzeMarketSentiment(symbol));
    }

    /**
     * 5. [핵심] 정량(ta4j) + 4대 ChromaDB AI 엔진 통합 하이브리드 의사결정 리포트 API (MySQL 자동 저장)
     */
    @GetMapping("/trading/decision")
    public ResponseEntity<IntegratedDecisionReport> getIntegratedDecision(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "D1") TimeFrame timeFrame,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(aggregatorService.generateDecisionReport(symbol, timeFrame, limit));
    }

    /**
     * 6. MySQL에 저장된 과거 의사결정 리포트 이력 조회 API
     */
    @GetMapping("/trading/history")
    public ResponseEntity<List<com.tem.spring.core.entity.DecisionReportEntity>> getDecisionHistory(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(aggregatorService.getDecisionHistory(symbol, limit));
    }

    /**
     * 7. 대화형 문맥 기억(Conversational Multi-Turn RAG) AI 리서치 질의응답 API
     */
    @PostMapping("/ai/research-chat")
    public ResponseEntity<com.tem.spring.ai.dto.AiResearchChatResponse> processResearchChat(
            @RequestBody com.tem.spring.ai.dto.AiResearchChatRequest request) {
        return ResponseEntity.ok(researchChatService.processResearchChat(request));
    }

    /**
     * 8. 간단한 Spring AI LLM 텍스트 생성 테스트 API
     */
    @GetMapping("/ai/generate")
    public ResponseEntity<java.util.Map<String, String>> generateText(
            @RequestParam(defaultValue = "Hello") String message) {
        String generation = researchChatService.generateSimpleResponse(message);
        return ResponseEntity.ok(java.util.Map.of("message", message, "generation", generation));
    }

    /**
     * 9. [vmstore 직접 조회] VectorStore 시맨틱 유사도 검색 API
     */
    @GetMapping("/ai/vectorstore/search")
    public ResponseEntity<java.util.Map<String, Object>> searchVectorStore(
            @RequestParam(defaultValue = "비트코인") String query,
            @RequestParam(defaultValue = "0.0") double threshold,
            @RequestParam(defaultValue = "10") int limit) {
        org.springframework.ai.vectorstore.VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null) {
            return ResponseEntity.ok(java.util.Map.of(
                    "status", "OFFLINE",
                    "message", "VectorStore bean is not available",
                    "results", java.util.List.of()
            ));
        }

        try {
            org.springframework.ai.vectorstore.SearchRequest request = org.springframework.ai.vectorstore.SearchRequest.query(query)
                    .withSimilarityThreshold(threshold)
                    .withTopK(limit);

            java.util.List<org.springframework.ai.document.Document> docs = vs.similaritySearch(request);

            java.util.List<java.util.Map<String, Object>> formatted = docs.stream().map(doc -> {
                java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("id", doc.getId());
                map.put("content", doc.getContent());
                map.put("metadata", doc.getMetadata());
                return map;
            }).toList();

            return ResponseEntity.ok(java.util.Map.of(
                    "status", "ONLINE",
                    "query", query,
                    "threshold", threshold,
                    "resultCount", formatted.size(),
                    "documents", formatted
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(java.util.Map.of(
                    "status", "FALLBACK_OFFLINE",
                    "query", query,
                    "message", "Embedding server(Ollama/BGE-M3) offline. Fallback to real-time BrightData RAG feed.",
                    "resultCount", 0,
                    "documents", java.util.List.of()
            ));
        }
    }

    /**
     * 10. [vmstore 상태 조회] VectorStore 상태 및 통계 API
     */
    @GetMapping("/ai/vectorstore/stats")
    public ResponseEntity<java.util.Map<String, Object>> getVectorStoreStats() {
        org.springframework.ai.vectorstore.VectorStore vs = vectorStoreProvider.getIfAvailable();
        return ResponseEntity.ok(java.util.Map.of(
                "status", vs != null ? "ACTIVE" : "INACTIVE",
                "vectorStoreType", vs != null ? vs.getClass().getSimpleName() : "None",
                "embeddingModel", "bge-m3:latest (1024-dimension)",
                "chromaPort", 8000,
                "timestamp", java.time.Instant.now().toString()
        ));
    }

    /**
     * 11. [MySQL 유저 질문 로그 조회] 유저 질문 및 RAG/AI 답변 감사 이력 API
     */
    @GetMapping("/ai/queries/recent")
    public ResponseEntity<List<com.tem.spring.ai.entity.UserQueryEntity>> getRecentUserQueries(
            @RequestParam(required = false) String symbol) {
        var repo = userQueryRepositoryProvider.getIfAvailable();
        if (repo == null) {
            return ResponseEntity.ok(List.of());
        }
        if (symbol != null && !symbol.isBlank()) {
            return ResponseEntity.ok(repo.findBySymbolOrderByCreatedAtDesc(symbol.toUpperCase()));
        }
        return ResponseEntity.ok(repo.findTop50ByOrderByCreatedAtDesc());
    }

    /**
     * 12. [인기 관심 키워드 분석] 유저들이 가장 많이 질문한 종목/키워드 통계 API
     */
    @GetMapping("/ai/queries/popular")
    public ResponseEntity<List<java.util.Map<String, Object>>> getPopularKeywords() {
        var repo = userQueryRepositoryProvider.getIfAvailable();
        if (repo == null) {
            return ResponseEntity.ok(List.of());
        }
        List<Object[]> raw = repo.findPopularKeywords();
        List<java.util.Map<String, Object>> result = raw.stream().map(row -> java.util.Map.of(
                "symbol", row[0] != null ? row[0] : "UNKNOWN",
                "queryCount", row[1] != null ? row[1] : 0
        )).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * 13. [사전 배치 크롤러 트리거] 유저 질문 분석 기반 타겟 발굴 및 vmstore 지식 사전 예열(Warmup)
     */
    @PostMapping("/ai/batch/warmup")
    public ResponseEntity<java.util.Map<String, Object>> triggerBatchWarmup(
            @RequestParam(required = false) List<String> symbols) {
        var warmupService = warmupBatchServiceProvider.getIfAvailable();
        if (warmupService == null) {
            return ResponseEntity.ok(java.util.Map.of("status", "UNAVAILABLE", "message", "ProactiveNewsWarmupBatchService not initialized"));
        }

        if (symbols != null && !symbols.isEmpty()) {
            return ResponseEntity.ok(warmupService.warmupSpecificSymbols(symbols));
        }
        return ResponseEntity.ok(warmupService.warmupAllTargets());
    }

    /**
     * 14. [배치 크롤러 통계] 사전 예열 이력 및 API 비용 절감 통계
     */
    @GetMapping("/ai/batch/status")
    public ResponseEntity<java.util.Map<String, Object>> getBatchWarmupStatus() {
        var warmupService = warmupBatchServiceProvider.getIfAvailable();
        if (warmupService == null) {
            return ResponseEntity.ok(java.util.Map.of("status", "UNAVAILABLE"));
        }
        return ResponseEntity.ok(warmupService.getWarmupStats());
    }

    /**
     * 15. [3번 & 4번 기능] 멀티채널 실시간 뉴스 피드 및 AI 호재/악재 감성 분석 조회 API
     */
    @GetMapping("/market/news/channel")
    public ResponseEntity<List<com.tem.spring.ai.dto.RichNewsItemDto>> getMultiChannelNews(
            @RequestParam(defaultValue = "ALL") String channel,
            @RequestParam(required = false) String symbol) {
        var scraper = brightDataNewsScraperServiceProvider.getIfAvailable();
        if (scraper == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(scraper.getRichNewsFeed(channel, symbol));
    }

    /**
     * 16. [10-Win Streak] 100 USDT 온체인 에스크로 보상 풀 실시간 잔액 및 당첨자 현황 조회 API
     */
    @GetMapping("/gamification/escrow-pool-status")
    public ResponseEntity<com.tem.spring.gamification.dto.EscrowPoolStatusDto> getEscrowPoolStatus() {
        return ResponseEntity.ok(streakRewardClaimService.getEscrowPoolStatus());
    }

    /**
     * 17. [10연승 미션] $10 USDT 보상 즉시 Claim (Payout) API
     */
    @PostMapping("/gamification/claim-streak-reward")
    public ResponseEntity<com.tem.spring.gamification.dto.ClaimStreakRewardResponse> claimStreakReward(
            @jakarta.validation.Valid @RequestBody com.tem.spring.gamification.dto.ClaimStreakRewardRequest request) {
        return ResponseEntity.ok(streakRewardClaimService.claimStreakReward(request));
    }

    /**
     * 18. [관리자] 에스크로 풀 예치금 및 활성화 상태 설정 API
     */
    @PostMapping("/gamification/admin/escrow-config")
    public ResponseEntity<com.tem.spring.gamification.dto.EscrowPoolStatusDto> updateEscrowConfig(
            @jakarta.validation.Valid @RequestBody com.tem.spring.gamification.dto.AdminEscrowConfigRequest request) {
        return ResponseEntity.ok(streakRewardClaimService.updateEscrowPoolCapacity(request));
    }

    /**
     * 19. [관리자] 에스크로 잔액 대표님 지갑으로 전액/일부 긴급 회수 (Sweep) API
     */
    @PostMapping("/gamification/admin/escrow-sweep")
    public ResponseEntity<com.tem.spring.gamification.dto.AdminEscrowSweepResponse> sweepEscrowFunds(
            @jakarta.validation.Valid @RequestBody com.tem.spring.gamification.dto.AdminEscrowSweepRequest request) {
        return ResponseEntity.ok(streakRewardClaimService.sweepEscrowFunds(request));
    }

    /**
     * 20. [관리자] 에스크로 감사 원장 및 트랜잭션 내역 조회 API
     */
    @GetMapping("/gamification/admin/escrow-logs")
    public ResponseEntity<java.util.List<com.tem.spring.gamification.dto.AdminEscrowAuditLogDto>> getAdminAuditLogs() {
        return ResponseEntity.ok(streakRewardClaimService.getAdminAuditLogs());
    }
}




