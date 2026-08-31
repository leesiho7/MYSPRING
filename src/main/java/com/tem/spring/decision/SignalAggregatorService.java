package com.tem.spring.decision;

import com.tem.spring.ai.service.ChartPatternVectorService;
import com.tem.spring.ai.service.DecisionMemoryService;
import com.tem.spring.ai.service.OllamaMarketAgentService;
import com.tem.spring.ai.service.PersonaAdvisoryService;
import com.tem.spring.ai.service.QuantContextPreprocessor;
import com.tem.spring.core.entity.CandleEntity;
import com.tem.spring.core.entity.DecisionReportEntity;
import com.tem.spring.core.model.*;
import com.tem.spring.core.repository.CandleRepository;
import com.tem.spring.core.repository.DecisionReportRepository;
import com.tem.spring.ingestion.service.MarketDataIngestionService;
import com.tem.spring.quant.adapter.BarSeriesMapper;
import com.tem.spring.quant.indicator.TechnicalIndicatorEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 정량적 지표(ta4j) + 4대 ChromaDB AI 엔진(뉴스 RAG, 차트 프랙탈, 장기 기억, 페르소나 자문)을 융합하고
 * MySQL에 실시간 원장을 영속화하는 코어 오케스트레이터 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalAggregatorService {

    private final MarketDataIngestionService ingestionService;
    private final BarSeriesMapper barSeriesMapper;
    private final TechnicalIndicatorEngine indicatorEngine;
    private final OllamaMarketAgentService ollamaService;

    private final QuantContextPreprocessor quantContextPreprocessor;
    private final com.tem.spring.ai.rag.FinancialNewsRagService ragService;

    // 4대 AI 벡터 서비스
    private final ChartPatternVectorService chartPatternService;
    private final DecisionMemoryService decisionMemoryService;
    private final PersonaAdvisoryService personaService;

    // MySQL 영속화 레포지토리
    private final DecisionReportRepository reportRepository;
    private final CandleRepository candleRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("tradingTaskExecutor")
    private java.util.concurrent.Executor tradingTaskExecutor;

    // 동시 중복 연산 방지 및 Request Collapsing을 위한 In-Flight 락 맵
    private final java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<IntegratedDecisionReport>> inFlightReports =
            new java.util.concurrent.ConcurrentHashMap<>();

    public IntegratedDecisionReport generateDecisionReport(String symbol, TimeFrame timeFrame, int candleLimit) {
        String lockKey = String.format("%s:%s",
                symbol != null ? symbol.toUpperCase() : "UNKNOWN",
                timeFrame != null ? timeFrame.name() : "DEFAULT");

        java.util.concurrent.Executor executor = (tradingTaskExecutor != null)
                ? tradingTaskExecutor
                : java.util.concurrent.ForkJoinPool.commonPool();

        // 동일 심볼 & 타임프레임 동시 인입 시 중복 연산(FastDTW 12스레드 폭주 및 LLM 중복 호출) 차단 & 결과 공유
        CompletableFuture<IntegratedDecisionReport> reportFuture = inFlightReports.computeIfAbsent(
                lockKey,
                k -> CompletableFuture.supplyAsync(() -> doGenerateDecisionReport(symbol, timeFrame, candleLimit), executor)
        );

        try {
            return reportFuture.join();
        } finally {
            inFlightReports.remove(lockKey, reportFuture);
        }
    }

    private IntegratedDecisionReport doGenerateDecisionReport(String symbol, TimeFrame timeFrame, int candleLimit) {
        log.info("[SignalAggregatorService] ⚡ Generating 4-Engine Integrated Report (Parallel CompletableFuture) for: {}", symbol);
        long startTime = System.currentTimeMillis();
        java.util.concurrent.Executor executor = (tradingTaskExecutor != null) ? tradingTaskExecutor : java.util.concurrent.ForkJoinPool.commonPool();

        // 0. 캔들 데이터 수집 및 비동기 벌크 영속화 (N+1 방지 & 커스텀 스레드 풀 적용)
        List<Candle> candles = ingestionService.getHistoricalData(symbol, timeFrame, candleLimit);
        persistCandlesAsync(symbol, candles);

        BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);

        // ── Phase 1: ta4j 정량 연산 & RAG 뉴스 스크래핑을 스레드 풀에서 병렬 실행 ──
        CompletableFuture<QuantitativeSignal> quantFuture = CompletableFuture.supplyAsync(
                () -> indicatorEngine.calculateSignals(series), executor);

        CompletableFuture<List<String>> headlinesFuture = CompletableFuture.supplyAsync(
                () -> ragService.retrieveRelevantNews(symbol), executor);

        // quant 완료 후 FastDTW 프랙탈 매칭 비동기 연결
        CompletableFuture<PatternInsight> patternFuture = quantFuture.thenApplyAsync(
                quant -> chartPatternService.analyzePatternSimilarity(symbol, candles, quant), executor);

        // Phase 1 완료 대기 (정량 지표 + 프랙탈 + 뉴스 수집 완료)
        CompletableFuture.allOf(quantFuture, headlinesFuture, patternFuture).join();

        QuantitativeSignal quant = quantFuture.join();
        List<String> headlines = headlinesFuture.join();
        PatternInsight pattern = patternFuture.join();

        // 정량/정성 융합 전처리 컨텍스트 빌드
        com.tem.spring.ai.dto.UnifiedMarketContext marketContext =
                quantContextPreprocessor.preprocess(symbol, candles, quant, pattern, headlines);

        // ── Phase 2: Ollama 정성 감성 분석 & 기억 검색 & 페르소나 자문을 병렬 실행 ──
        CompletableFuture<QualitativeInsight> qualFuture = CompletableFuture.supplyAsync(
                () -> ollamaService.analyzeMarketSentiment(symbol, marketContext), executor);

        CompletableFuture<String> reflectionFuture = qualFuture.thenApplyAsync(
                qual -> decisionMemoryService.retrieveRelevantReflection(symbol, quant, qual), executor);

        CompletableFuture<PersonaAdvice> personaFuture = qualFuture.thenApplyAsync(
                qual -> personaService.advise(symbol, marketContext, quant, qual), executor);

        // Phase 2 완료 대기
        CompletableFuture.allOf(qualFuture, reflectionFuture, personaFuture).join();

        QualitativeInsight qual = qualFuture.join();
        String agentReflection = reflectionFuture.join();
        PersonaAdvice personaAdvice = personaFuture.join();

        // 4. 최종 신호 융합 (변동성 전이 레짐 분류 & 동적 가중치 & 결정론적 앙상블 적용)
        IntegratedDecisionReport report = fuseDecision(symbol, quant, qual, pattern, agentReflection, personaAdvice, marketContext);

        // 5. MySQL 원장 영속화 및 ChromaDB 메모리 기록
        saveReportAndRecordMemory(report);

        log.info("[SignalAggregatorService] 🚀 Parallel Integrated Report completed in {}ms for {} [Regime: {}]",
                (System.currentTimeMillis() - startTime), symbol, report.getMarketRegime());

        return report;
    }

    private IntegratedDecisionReport fuseDecision(String symbol,
                                                  QuantitativeSignal quant,
                                                  QualitativeInsight qual,
                                                  PatternInsight pattern,
                                                  String agentReflection,
                                                  PersonaAdvice personaAdvice) {
        return fuseDecision(symbol, quant, qual, pattern, agentReflection, personaAdvice, null);
    }

    private IntegratedDecisionReport fuseDecision(String symbol,
                                                  QuantitativeSignal quant,
                                                  QualitativeInsight qual,
                                                  PatternInsight pattern,
                                                  String agentReflection,
                                                  PersonaAdvice personaAdvice,
                                                  com.tem.spring.ai.dto.UnifiedMarketContext context) {
        // ── 1. Market Regime Classifier (변동성 전이에 따른 동적 가중치 가변화) ──
        double delta = (context != null) ? Math.abs(context.getStrikeDeltaPct()) : 0.0;
        double rsi = (quant != null) ? quant.getRsi() : 50.0;
        double quantScore = (quant != null) ? quant.getQuantScore() : 0.0;

        String marketRegime;
        double quantWeight;
        double qualWeight;
        double patternWeight;

        if (delta >= 2.5) {
            // 급변기 / 뉴스 충격 구간: 차트 후행성을 극복하기 위해 뉴스 감성 및 다이버전스 비중 강화
            marketRegime = "HIGH_VOLATILITY";
            quantWeight = 0.30;
            qualWeight = 0.50;
            patternWeight = 0.20;
        } else if (delta < 0.6 && rsi >= 45.0 && rsi <= 55.0 && Math.abs(quantScore) < 0.25) {
            // 횡보장 / 수렴 구간: 뉴스 노이즈를 배제하고 과거 프랙탈 패턴 재현 확률 강화
            marketRegime = "CONSOLIDATION";
            quantWeight = 0.40;
            qualWeight = 0.15;
            patternWeight = 0.45;
        } else if (Math.abs(quantScore) >= 0.40) {
            // 강한 추세장: 정량 추세 지표 우선
            marketRegime = "TRENDING";
            quantWeight = 0.50;
            qualWeight = 0.30;
            patternWeight = 0.20;
        } else {
            marketRegime = "NORMAL";
            quantWeight = 0.45;
            qualWeight = 0.30;
            patternWeight = 0.25;
        }

        double winRate = (pattern != null) ? pattern.getHistoricalWinRate() : 0.50;
        double expectedReturn = (pattern != null) ? pattern.getExpectedReturn5Day() : 0.0;
        double patternScore = (winRate - 0.5) * 2.0; // 0.8 winRate -> +0.6 score
        double qualScore = (qual != null) ? qual.getSentimentScore() : 0.0;

        double baseTotalScore = (quantScore * quantWeight)
                + (qualScore * qualWeight)
                + (patternScore * patternWeight);

        double finalScore = baseTotalScore;
        String divergenceRisk = "정상: 정량 지표와 정성 시장 분위기가 일치합니다.";
        String reason;

        // 신호 괴리(Divergence) 감지 및 리스크 페널티
        if (quantScore > 0.3 && qualScore < -0.2) {
            divergenceRisk = "주의 (Divergence): 차트는 기술적 반등 신호이나, 뉴스는 악재가 감지되었습니다. (가짜 반등 주의)";
            finalScore *= 0.5;
        } else if (quantScore < -0.3 && qualScore > 0.2) {
            divergenceRisk = "주의 (Divergence): 뉴스는 호재이나, 기술적 과매수 또는 저항선에 도달하여 조정 위험이 있습니다.";
            finalScore *= 0.5;
        }

        // ── [Hard Rule 1] 정량 지표와 패턴 승률이 모두 하락세인데, AI 혼자 STRONG_BUY를 주장할 경우 HOLD 강제 수정 ──
        boolean quantBearish = quantScore < -0.10;
        boolean patternBearish = winRate < 0.45;
        boolean aiSuperBullish = qualScore > 0.30;
        if (quantBearish && patternBearish && aiSuperBullish) {
            divergenceRisk = "🚨 [결정론적 하드 게이트] 차트 정량 지표와 FastDTW 승률이 하락세이나, AI 단독 과열 매수가 감지되어 환각 방지를 위해 HOLD로 강제 다운그레이드되었습니다.";
            finalScore = 0.0;
        }

        // 최종 매매 권고 산출
        ActionType finalAction;
        if (finalScore >= 0.50) {
            // ── [Hard Rule 2] 점수가 0.50 이상이어도 과거 프랙탈 패턴 승률이 40% 미만(또는 기대수익률 < 0)이면 STRONG_BUY 금지 ──
            if (winRate < 0.40 || expectedReturn < 0.0) {
                finalAction = ActionType.HOLD;
                reason = String.format("종합 스코어(%.2f)는 상승 구간이나, FastDTW 과거 프랙탈 승률(%.0f%%) 및 기대수익률(%.1f%%)이 하락 리스크를 경고하여 STRONG_BUY를 금지하고 HOLD로 하드 룰 수정함",
                        finalScore, winRate * 100, expectedReturn * 100);
            } else {
                finalAction = ActionType.STRONG_BUY;
                reason = String.format("ta4j(%.2f), 뉴스감성(%.2f), 과거패턴승률(%.0f%%) 3박자가 강력한 상승 추세를 지지함 [레짐: %s]",
                        quantScore, qualScore, winRate * 100, marketRegime);
            }
        } else if (finalScore >= 0.18) {
            if (winRate < 0.40 && expectedReturn < -0.02) {
                finalAction = ActionType.HOLD;
                reason = String.format("단기 매수 신호이나 FastDTW 프랙탈 과거 승률(%.0f%%) 저조로 인하여 안전을 위해 HOLD로 보수적 하향 조정 (종합점수: %.2f)", winRate * 100, finalScore);
            } else {
                finalAction = ActionType.BUY;
                reason = String.format("정량 지표와 뉴스 모멘텀, 과거 프랙탈 패턴 기반 매수 우위 포지션 유지 (종합점수: %.2f)", finalScore);
            }
        } else if (finalScore <= -0.50) {
            finalAction = ActionType.STRONG_SELL;
            reason = String.format("기술적 하락 추세와 악재, 불리한 과거 패턴이 중첩되어 즉시 리스크 관리 권고 (종합점수: %.2f)", finalScore);
        } else if (finalScore <= -0.18) {
            finalAction = ActionType.SELL;
            reason = String.format("단기 차익 실현 및 보수적 관점의 비중 축소 권고 (종합점수: %.2f)", finalScore);
        } else {
            finalAction = ActionType.HOLD;
            reason = String.format("지표 간 상충 또는 중립 구간으로 신규 포지션 진입을 보류하고 관망 권고 (종합점수: %.2f)", finalScore);
        }

        return IntegratedDecisionReport.builder()
                .symbol(symbol)
                .finalAction(finalAction)
                .totalScore(Math.round(finalScore * 100.0) / 100.0)
                .divergenceRisk(divergenceRisk)
                .decisionReason(reason)
                .quantSignal(quant)
                .qualInsight(qual)
                .patternInsight(pattern)
                .agentReflection(agentReflection)
                .personaAdvice(personaAdvice)
                .marketRegime(marketRegime)
                .llmModelName("qwen2.5:14b")
                .promptVersion("v2.1")
                .quantWeight(quantWeight)
                .qualWeight(qualWeight)
                .patternWeight(patternWeight)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private void saveReportAndRecordMemory(IntegratedDecisionReport report) {
        try {
            // 1. MySQL 영속화 (Model Lineage 및 레짐 정보 함께 영속화)
            DecisionReportEntity entity = DecisionReportEntity.builder()
                    .symbol(report.getSymbol())
                    .finalAction(report.getFinalAction())
                    .totalScore(report.getTotalScore())
                    .divergenceRisk(report.getDivergenceRisk())
                    .decisionReason(report.getDecisionReason())
                    .quantScore(report.getQuantSignal() != null ? report.getQuantSignal().getQuantScore() : 0.0)
                    .sentimentScore(report.getQualInsight() != null ? report.getQualInsight().getSentimentScore() : 0.0)
                    .patternWinRate(report.getPatternInsight() != null ? report.getPatternInsight().getHistoricalWinRate() : 0.5)
                    .agentReflection(report.getAgentReflection())
                    .marketRegime(report.getMarketRegime())
                    .llmModelName(report.getLlmModelName())
                    .promptVersion(report.getPromptVersion())
                    .quantWeight(report.getQuantWeight())
                    .qualWeight(report.getQualWeight())
                    .patternWeight(report.getPatternWeight())
                    .generatedAt(report.getGeneratedAt() != null ? report.getGeneratedAt() : LocalDateTime.now())
                    .build();

            reportRepository.save(entity);
            log.info("[SignalAggregatorService] Saved decision report to MySQL for {}", report.getSymbol());

            // 2. ChromaDB 의사결정 장기 기억 축적
            decisionMemoryService.recordDecision(
                    report.getSymbol(),
                    report.getFinalAction(),
                    report.getTotalScore(),
                    report.getDecisionReason()
            );
        } catch (Exception e) {
            log.warn("[SignalAggregatorService] Failed to persist report: {}", e.getMessage());
        }
    }

    private void persistCandlesAsync(String symbol, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;
        java.util.concurrent.Executor executor = (tradingTaskExecutor != null) ? tradingTaskExecutor : java.util.concurrent.ForkJoinPool.commonPool();
        CompletableFuture.runAsync(() -> {
            try {
                java.util.Set<java.time.ZonedDateTime> incomingTimestamps = candles.stream()
                        .map(Candle::getTimestamp)
                        .collect(java.util.stream.Collectors.toSet());

                java.util.Set<java.time.ZonedDateTime> existing = candleRepository.findExistingTimestamps(symbol, incomingTimestamps);

                List<CandleEntity> toSave = candles.stream()
                        .filter(c -> existing == null || !existing.contains(c.getTimestamp()))
                        .map(c -> CandleEntity.builder()
                                .symbol(symbol)
                                .timestamp(c.getTimestamp())
                                .open(c.getOpen())
                                .high(c.getHigh())
                                .low(c.getLow())
                                .close(c.getClose())
                                .volume(c.getVolume())
                                .build())
                        .toList();

                if (!toSave.isEmpty()) {
                    candleRepository.saveAll(toSave);
                    log.info("[SignalAggregatorService] 📦 Bulk persisted {} new candles (N+1 query removed) to MySQL for {}", toSave.size(), symbol);
                }
            } catch (Exception e) {
                log.debug("[SignalAggregatorService] Candle persistence note: {}", e.getMessage());
            }
        }, executor);
    }

    public List<DecisionReportEntity> getDecisionHistory(String symbol, int limit) {
        return reportRepository.findRecentReportsBySymbol(symbol, org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
