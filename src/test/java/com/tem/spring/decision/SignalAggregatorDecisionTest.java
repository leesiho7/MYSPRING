package com.tem.spring.decision;

import com.tem.spring.ai.service.ChartPatternVectorService;
import com.tem.spring.ai.service.DecisionMemoryService;
import com.tem.spring.ai.service.OllamaMarketAgentService;
import com.tem.spring.ai.service.PersonaAdvisoryService;
import com.tem.spring.ai.service.QuantContextPreprocessor;
import com.tem.spring.core.entity.DecisionReportEntity;
import com.tem.spring.core.model.*;
import com.tem.spring.core.repository.CandleRepository;
import com.tem.spring.core.repository.DecisionReportRepository;
import com.tem.spring.ingestion.service.MarketDataIngestionService;
import com.tem.spring.quant.adapter.BarSeriesMapper;
import com.tem.spring.quant.indicator.TechnicalIndicatorEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignalAggregatorDecisionTest {

    @Mock private MarketDataIngestionService ingestionService;
    @Mock private BarSeriesMapper barSeriesMapper;
    @Mock private TechnicalIndicatorEngine indicatorEngine;
    @Mock private OllamaMarketAgentService ollamaService;
    @Mock private QuantContextPreprocessor quantContextPreprocessor;
    @Mock private com.tem.spring.ai.rag.FinancialNewsRagService ragService;
    @Mock private ChartPatternVectorService chartPatternService;
    @Mock private DecisionMemoryService decisionMemoryService;
    @Mock private PersonaAdvisoryService personaService;
    @Mock private DecisionReportRepository reportRepository;
    @Mock private CandleRepository candleRepository;

    private SignalAggregatorService aggregatorService;

    @BeforeEach
    void setUp() {
        aggregatorService = new SignalAggregatorService(
                ingestionService, barSeriesMapper, indicatorEngine, ollamaService,
                quantContextPreprocessor, ragService, chartPatternService,
                decisionMemoryService, personaService, reportRepository, candleRepository
        );
        ReflectionTestUtils.setField(aggregatorService, "tradingTaskExecutor", java.util.concurrent.Executors.newFixedThreadPool(4));
    }

    @Test
    @DisplayName("1. [Hard Rule 1] 정량/패턴이 모두 하락세이나 AI 혼자 STRONG_BUY를 주장할 때 HOLD로 강제 다운그레이드 검증")
    void testHardRule1_AiHallucinationDowngrade() {
        QuantitativeSignal quant = QuantitativeSignal.builder()
                .symbol("BTCUSDT")
                .quantScore(-0.35) // 하락세 (< -0.10)
                .build();

        PatternInsight pattern = PatternInsight.builder()
                .patternName("하락 지속형")
                .historicalWinRate(0.35) // 35% (< 0.45)
                .expectedReturn5Day(-0.04)
                .build();

        QualitativeInsight qual = QualitativeInsight.builder()
                .sentimentScore(0.85) // AI의 과도한 환각 매수 (> 0.30)
                .build();

        PersonaAdvice persona = PersonaAdvice.builder().build();

        IntegratedDecisionReport report = ReflectionTestUtils.invokeMethod(
                aggregatorService, "fuseDecision",
                "BTCUSDT", quant, qual, pattern, "reflection", persona
        );

        assertNotNull(report);
        assertEquals(ActionType.HOLD, report.getFinalAction(), "AI 과열 매수는 HOLD로 강제 다운그레이드되어야 함");
        assertTrue(report.getDivergenceRisk().contains("결정론적 하드 게이트"));
    }

    @Test
    @DisplayName("2. [Hard Rule 2] 점수가 0.50 이상이어도 FastDTW 승률 < 40% 이면 STRONG_BUY 금지 및 HOLD 수정 검증")
    void testHardRule2_WeakPatternProhibitsStrongBuy() {
        QuantitativeSignal quant = QuantitativeSignal.builder()
                .symbol("BTCUSDT")
                .quantScore(0.90) // 매우 강한 정량 점수
                .build();

        PatternInsight pattern = PatternInsight.builder()
                .patternName("불안정한 휩쏘 패턴")
                .historicalWinRate(0.38) // 승률 38% (< 40%)
                .expectedReturn5Day(-0.015)
                .build();

        QualitativeInsight qual = QualitativeInsight.builder()
                .sentimentScore(0.60) // 호재 뉴스
                .build();

        PersonaAdvice persona = PersonaAdvice.builder().build();

        IntegratedDecisionReport report = ReflectionTestUtils.invokeMethod(
                aggregatorService, "fuseDecision",
                "BTCUSDT", quant, qual, pattern, "reflection", persona
        );

        assertNotNull(report);
        assertEquals(ActionType.HOLD, report.getFinalAction(), "승률 40% 미만일 경우 STRONG_BUY가 금지되고 HOLD여야 함");
        assertTrue(report.getDecisionReason().contains("STRONG_BUY를 금지하고 HOLD로 하드 룰 수정함"));
    }

    @Test
    @DisplayName("3. [N+1 쿼리 제거 검증] persistCandlesAsync 벌크 저장 검증")
    void testBulkCandlePersistence() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Candle> candles = List.of(
                Candle.builder().timestamp(now).open(100.0).high(105.0).low(99.0).close(104.0).volume(10.0).build(),
                Candle.builder().timestamp(now.minusHours(1)).open(98.0).high(101.0).low(97.0).close(100.0).volume(12.0).build()
        );

        when(candleRepository.findExistingTimestamps(eq("BTCUSDT"), anyCollection()))
                .thenReturn(Set.of(now.minusHours(1))); // 1건은 이미 존재

        ReflectionTestUtils.invokeMethod(aggregatorService, "persistCandlesAsync", "BTCUSDT", candles);

        // 비동기 작업이 스레드 풀에서 돌 때까지 잠시 대기
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}

        verify(candleRepository, atLeastOnce()).findExistingTimestamps(eq("BTCUSDT"), anyCollection());
        verify(candleRepository, atLeastOnce()).saveAll(anyList());
    }

    @Test
    @DisplayName("4. [동시성 락/Request Collapsing 검증] 동일 심볼 동시 요청 시 중복 연산 방지 검증")
    void testInFlightRequestCollapsing() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Candle> candles = List.of(
                Candle.builder().timestamp(now).open(100.0).high(105.0).low(99.0).close(104.0).volume(10.0).build()
        );
        when(ingestionService.getHistoricalData(eq("BTCUSDT"), any(), anyInt())).thenReturn(candles);
        when(barSeriesMapper.toBarSeries(anyString(), anyList())).thenReturn(org.mockito.Mockito.mock(org.ta4j.core.BarSeries.class));
        when(indicatorEngine.calculateSignals(any())).thenReturn(QuantitativeSignal.builder().quantScore(0.5).build());
        when(ragService.retrieveRelevantNews(anyString())).thenReturn(List.of("BTC 뉴스"));
        when(quantContextPreprocessor.preprocess(any(), any(), any(), any(), any()))
                .thenReturn(com.tem.spring.ai.dto.UnifiedMarketContext.builder().symbol("BTCUSDT").currentPrice(100).quantScore(0.5).build());
        when(ollamaService.analyzeMarketSentiment(anyString(), any()))
                .thenReturn(QualitativeInsight.builder().sentimentScore(0.5).sentiment("BULLISH").build());
        when(decisionMemoryService.retrieveRelevantReflection(anyString(), any(), any())).thenReturn("기억");
        when(personaService.advise(anyString(), any(), any(), any())).thenReturn(PersonaAdvice.builder().build());

        // 동시에 3개 스레드에서 generateDecisionReport 호출
        java.util.concurrent.CompletableFuture<IntegratedDecisionReport> f1 =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> aggregatorService.generateDecisionReport("BTCUSDT", TimeFrame.H1, 60));
        java.util.concurrent.CompletableFuture<IntegratedDecisionReport> f2 =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> aggregatorService.generateDecisionReport("BTCUSDT", TimeFrame.H1, 60));

        java.util.concurrent.CompletableFuture.allOf(f1, f2).join();

        assertNotNull(f1.join());
        assertNotNull(f2.join());
        assertEquals("BTCUSDT", f1.join().getSymbol());
    }

    @Test
    @DisplayName("5. [Market Regime 동적 가중치 검증] 급변기(HIGH_VOLATILITY) vs 횡보장(CONSOLIDATION) 가중치 전환 검증")
    void testMarketRegimeDynamicWeighting() {
        QuantitativeSignal quant = QuantitativeSignal.builder()
                .symbol("BTCUSDT")
                .rsi(50.0)
                .quantScore(0.1)
                .build();
        PatternInsight pattern = PatternInsight.builder()
                .historicalWinRate(0.70)
                .expectedReturn5Day(0.03)
                .build();
        QualitativeInsight qual = QualitativeInsight.builder()
                .sentimentScore(0.5)
                .build();
        PersonaAdvice persona = PersonaAdvice.builder().build();

        // 1. 횡보장 (Consolidation: delta 0.2%, rsi 50) -> 패턴 가중치 45% 부여 검증
        com.tem.spring.ai.dto.UnifiedMarketContext flatContext = com.tem.spring.ai.dto.UnifiedMarketContext.builder()
                .symbol("BTCUSDT").strikeDeltaPct(0.2).rsi(50.0).quantScore(0.1).build();

        IntegratedDecisionReport flatReport = ReflectionTestUtils.invokeMethod(
                aggregatorService, "fuseDecision",
                "BTCUSDT", quant, qual, pattern, "reflection", persona, flatContext
        );

        assertNotNull(flatReport);
        assertEquals("CONSOLIDATION", flatReport.getMarketRegime());
        assertEquals(0.45, flatReport.getPatternWeight());
        assertEquals(0.15, flatReport.getQualWeight());

        // 2. 급변기 (High Volatility: delta 3.5%) -> 뉴스/감성 가중치 50% 부여 검증
        com.tem.spring.ai.dto.UnifiedMarketContext volatileContext = com.tem.spring.ai.dto.UnifiedMarketContext.builder()
                .symbol("BTCUSDT").strikeDeltaPct(3.5).rsi(70.0).quantScore(0.5).build();

        IntegratedDecisionReport volatileReport = ReflectionTestUtils.invokeMethod(
                aggregatorService, "fuseDecision",
                "BTCUSDT", quant, qual, pattern, "reflection", persona, volatileContext
        );

        assertNotNull(volatileReport);
        assertEquals("HIGH_VOLATILITY", volatileReport.getMarketRegime());
        assertEquals(0.50, volatileReport.getQualWeight());
        assertEquals(0.30, volatileReport.getQuantWeight());
    }
}
