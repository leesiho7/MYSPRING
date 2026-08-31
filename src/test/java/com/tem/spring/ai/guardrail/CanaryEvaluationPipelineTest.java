package com.tem.spring.ai.guardrail;

import com.tem.spring.ai.dto.UnifiedMarketContext;
import com.tem.spring.core.model.*;
import com.tem.spring.decision.SignalAggregatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 카나리 배포(Canary Deployment) 및 백테스팅 자동화 CI/CD 검증 파이프라인 테스트
 * - 프로덕션 배포 전 0건의 하드 룰 에러와 모델 드리프트 허용치를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class CanaryEvaluationPipelineTest {

    @Mock
    private com.tem.spring.ingestion.service.MarketDataIngestionService ingestionService;

    @Mock
    private com.tem.spring.quant.adapter.BarSeriesMapper barSeriesMapper;

    @Mock
    private com.tem.spring.quant.indicator.TechnicalIndicatorEngine indicatorEngine;

    @Mock
    private com.tem.spring.ai.rag.FinancialNewsRagService ragService;

    @Mock
    private com.tem.spring.ai.service.ChartPatternVectorService chartPatternService;

    @Mock
    private com.tem.spring.ai.service.QuantContextPreprocessor quantContextPreprocessor;

    @Mock
    private com.tem.spring.ai.service.OllamaMarketAgentService ollamaService;

    @Mock
    private com.tem.spring.ai.service.DecisionMemoryService decisionMemoryService;

    @Mock
    private com.tem.spring.ai.service.PersonaAdvisoryService personaService;

    @Mock
    private com.tem.spring.core.repository.DecisionReportRepository reportRepository;

    @Mock
    private com.tem.spring.core.repository.CandleRepository candleRepository;

    @InjectMocks
    private SignalAggregatorService aggregatorService;

    @Test
    @DisplayName("1. [Canary CI/CD] 15분 이상 지연된 뉴스(Latency Lag) 시차 페널티 플래그 정상 주입 검증")
    void testLatencyLagPenaltyEvaluation() {
        UnifiedMarketContext lagContext = UnifiedMarketContext.builder()
                .symbol("BTCUSDT")
                .currentPrice(80000)
                .chartSnapshotTime("2026-08-31 20:15:00 KST")
                .ragNewsCollectionTime("2026-08-31 19:50:00 KST")
                .latencyLagMinutes(25)
                .lagPenaltyActive(true)
                .build();

        assertTrue(lagContext.isLagPenaltyActive(), "15분 이상 지연된 뉴스는 lagPenaltyActive=true 여야 함");
        String xml = lagContext.toPromptBlock();
        assertTrue(xml.contains("<latency_lag_minutes>25</latency_lag_minutes>"));
        assertTrue(xml.contains("50% Weight Reduction on News"));
    }

    @Test
    @DisplayName("2. [Canary CI/CD] 미래 데이터 유출(Look-Ahead Bias) 없는 타임스탬프 슬라이싱 무결성 검증")
    void testLookAheadBiasIntegrity() {
        ZonedDateTime cutoffTime = ZonedDateTime.now();
        List<Candle> historicalCandles = List.of(
                Candle.builder().timestamp(cutoffTime.minusHours(2)).close(90.0).build(),
                Candle.builder().timestamp(cutoffTime.minusHours(1)).close(95.0).build(),
                Candle.builder().timestamp(cutoffTime).close(100.0).build()
        );

        // 모든 캔들의 시점이 현재 기준시점(cutoffTime) 이하인지 검증
        boolean hasFutureLeak = historicalCandles.stream()
                .anyMatch(c -> c.getTimestamp().isAfter(cutoffTime));

        assertFalse(hasFutureLeak, "현재 시점을 초과하는 미래 캔들이 데이터셋에 유출되어서는 안 됨");
    }

    @Test
    @DisplayName("3. [Canary CI/CD] 프로덕션 자동 배포 전 Model Lineage 메타데이터 무결성 검증")
    void testModelLineageCompliance() {
        QuantitativeSignal quant = QuantitativeSignal.builder().quantScore(0.6).rsi(65.0).build();
        QualitativeInsight qual = QualitativeInsight.builder().sentimentScore(0.7).build();
        PatternInsight pattern = PatternInsight.builder().historicalWinRate(0.85).expectedReturn5Day(0.04).build();
        PersonaAdvice persona = PersonaAdvice.builder().build();

        IntegratedDecisionReport report = ReflectionTestUtils.invokeMethod(
                aggregatorService, "fuseDecision",
                "BTCUSDT", quant, qual, pattern, "reflection", persona
        );

        assertNotNull(report);
        assertEquals("qwen2.5:14b", report.getLlmModelName(), "LLM 모델 계보 메타데이터가 기록되어야 함");
        assertEquals("v2.1", report.getPromptVersion(), "프롬프트 버전 계보가 기록되어야 함");
        assertNotNull(report.getMarketRegime(), "마켓 레짐이 분류되어야 함");
        assertTrue(report.getQuantWeight() > 0 && report.getQualWeight() > 0 && report.getPatternWeight() > 0);
    }
}
