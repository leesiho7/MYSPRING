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

    public IntegratedDecisionReport generateDecisionReport(String symbol, TimeFrame timeFrame, int candleLimit) {
        log.info("[SignalAggregatorService] Generating 4-Engine Integrated Report with Preprocessed Context for: {}", symbol);

        // 0. 캔들 데이터 수집
        List<Candle> candles = ingestionService.getHistoricalData(symbol, timeFrame, candleLimit);
        persistCandlesAsync(symbol, candles);

        BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);

        // 1. ta4j 정량 지표 및 FastDTW 8000 프랙탈 매칭
        QuantitativeSignal quant = indicatorEngine.calculateSignals(series);
        PatternInsight pattern = chartPatternService.analyzePatternSimilarity(symbol, candles, quant);

        // 2. RAG 금융 뉴스 수집 및 정량/정성 융합 전처리 컨텍스트 빌드
        List<String> headlines = ragService.retrieveRelevantNews(symbol);
        com.tem.spring.ai.dto.UnifiedMarketContext marketContext =
                quantContextPreprocessor.preprocess(symbol, candles, quant, pattern, headlines);

        // 3. 전처리된 컨텍스트를 주입하여 정성적 AI 리서치 및 페르소나 브리핑 생성
        QualitativeInsight qual = ollamaService.analyzeMarketSentiment(symbol, marketContext);
        String agentReflection = decisionMemoryService.retrieveRelevantReflection(symbol, quant, qual);
        PersonaAdvice personaAdvice = personaService.advise(symbol, marketContext, quant, qual);

        // 4. 최종 신호 융합
        IntegratedDecisionReport report = fuseDecision(symbol, quant, qual, pattern, agentReflection, personaAdvice);

        // 5. MySQL 원장 영속화 및 ChromaDB 메모리 기록
        saveReportAndRecordMemory(report);

        return report;
    }

    private IntegratedDecisionReport fuseDecision(String symbol,
                                                  QuantitativeSignal quant,
                                                  QualitativeInsight qual,
                                                  PatternInsight pattern,
                                                  String agentReflection,
                                                  PersonaAdvice personaAdvice) {
        double quantWeight = 0.50;
        double qualWeight = 0.30;
        double patternWeight = 0.20;

        double patternScore = (pattern.getHistoricalWinRate() - 0.5) * 2.0; // 0.8 winRate -> +0.6 score
        double baseTotalScore = (quant.getQuantScore() * quantWeight)
                + (qual.getSentimentScore() * qualWeight)
                + (patternScore * patternWeight);

        double finalScore = baseTotalScore;
        String divergenceRisk = "정상: 정량 지표와 정성 시장 분위기가 일치합니다.";
        String reason;

        // 신호 괴리(Divergence) 감지 및 리스크 페널티
        if (quant.getQuantScore() > 0.3 && qual.getSentimentScore() < -0.2) {
            divergenceRisk = "주의 (Divergence): 차트는 기술적 반등 신호이나, 뉴스는 악재가 감지되었습니다. (가짜 반등 주의)";
            finalScore *= 0.5;
        } else if (quant.getQuantScore() < -0.3 && qual.getSentimentScore() > 0.2) {
            divergenceRisk = "주의 (Divergence): 뉴스는 호재이나, 기술적 과매수 또는 저항선에 도달하여 조정 위험이 있습니다.";
            finalScore *= 0.5;
        }

        // 최종 매매 권고 산출
        ActionType finalAction;
        if (finalScore >= 0.50) {
            finalAction = ActionType.STRONG_BUY;
            reason = String.format("ta4j(%.2f), 뉴스감성(%.2f), 과거패턴승률(%.0f%%) 3박자가 강력한 상승 추세를 지지함",
                    quant.getQuantScore(), qual.getSentimentScore(), pattern.getHistoricalWinRate() * 100);
        } else if (finalScore >= 0.18) {
            finalAction = ActionType.BUY;
            reason = String.format("정량 지표와 뉴스 모멘텀, 과거 프랙탈 패턴 기반 매수 우위 포지션 유지 (종합점수: %.2f)", finalScore);
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
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private void saveReportAndRecordMemory(IntegratedDecisionReport report) {
        try {
            // 1. MySQL 영속화
            DecisionReportEntity entity = DecisionReportEntity.builder()
                    .symbol(report.getSymbol())
                    .finalAction(report.getFinalAction())
                    .totalScore(report.getTotalScore())
                    .divergenceRisk(report.getDivergenceRisk())
                    .decisionReason(report.getDecisionReason())
                    .quantScore(report.getQuantSignal() != null ? report.getQuantSignal().getQuantScore() : 0)
                    .sentimentScore(report.getQualInsight() != null ? report.getQualInsight().getSentimentScore() : 0)
                    .patternWinRate(report.getPatternInsight() != null ? report.getPatternInsight().getHistoricalWinRate() : 0)
                    .agentReflection(report.getAgentReflection())
                    .generatedAt(report.getGeneratedAt())
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
        CompletableFuture.runAsync(() -> {
            try {
                int savedCount = 0;
                for (Candle c : candles) {
                    if (!candleRepository.existsBySymbolAndTimestamp(symbol, c.getTimestamp())) {
                        candleRepository.save(CandleEntity.builder()
                                .symbol(symbol)
                                .timestamp(c.getTimestamp())
                                .open(c.getOpen())
                                .high(c.getHigh())
                                .low(c.getLow())
                                .close(c.getClose())
                                .volume(c.getVolume())
                                .build());
                        savedCount++;
                    }
                }
                if (savedCount > 0) {
                    log.info("[SignalAggregatorService] Persisted {} new candles to MySQL for {}", savedCount, symbol);
                }
            } catch (Exception e) {
                log.debug("[SignalAggregatorService] Candle persistence note: {}", e.getMessage());
            }
        });
    }

    public List<DecisionReportEntity> getDecisionHistory(String symbol, int limit) {
        return reportRepository.findRecentReportsBySymbol(symbol, org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
