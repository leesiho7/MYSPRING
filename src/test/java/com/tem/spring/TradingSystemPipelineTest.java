package com.tem.spring;

import com.tem.spring.core.model.BacktestResult;
import com.tem.spring.core.model.Candle;
import com.tem.spring.core.model.IntegratedDecisionReport;
import com.tem.spring.core.model.QuantitativeSignal;
import com.tem.spring.core.model.TimeFrame;
import com.tem.spring.decision.SignalAggregatorService;
import com.tem.spring.ingestion.service.MarketDataIngestionService;
import com.tem.spring.quant.adapter.BarSeriesMapper;
import com.tem.spring.quant.indicator.TechnicalIndicatorEngine;
import com.tem.spring.quant.strategy.BacktestingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.ta4j.core.BarSeries;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TradingSystemPipelineTest {

    @Autowired
    private MarketDataIngestionService ingestionService;

    @Autowired
    private BarSeriesMapper barSeriesMapper;

    @Autowired
    private TechnicalIndicatorEngine indicatorEngine;

    @Autowired
    private BacktestingEngine backtestingEngine;

    @Autowired
    private SignalAggregatorService aggregatorService;

    @Test
    @DisplayName("1. OpenBB 스타일 데이터 수집 및 BarSeries 변환 테스트")
    void testDataIngestionAndBarSeriesConversion() {
        String symbol = "BTCUSDT";
        List<Candle> candles = ingestionService.getHistoricalData(symbol, TimeFrame.D1, 50);

        assertNotNull(candles);
        assertFalse(candles.isEmpty(), "수집된 캔들 데이터가 비어있지 않아야 합니다.");
        assertTrue(candles.size() >= 30, "충분한 수의 캔들이 수집되어야 합니다.");

        BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);
        assertEquals(symbol, series.getName());
        assertEquals(candles.size(), series.getBarCount());
    }

    @Test
    @DisplayName("2. ta4j 기술적 지표 연산 테스트")
    void testTechnicalIndicatorCalculations() {
        String symbol = "BTCUSDT";
        List<Candle> candles = ingestionService.getHistoricalData(symbol, TimeFrame.D1, 60);
        BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);

        QuantitativeSignal signal = indicatorEngine.calculateSignals(series);

        assertNotNull(signal);
        assertEquals(symbol, signal.getSymbol());
        assertTrue(signal.getRsi() >= 0 && signal.getRsi() <= 100, "RSI는 0~100 사이여야 합니다.");
        assertTrue(signal.getQuantScore() >= -1.0 && signal.getQuantScore() <= 1.0, "정량 스코어는 -1.0~1.0 사이여야 합니다.");
        assertNotNull(signal.getSuggestedAction());
    }

    @Test
    @DisplayName("3. ta4j 백테스팅 엔진 실행 테스트")
    void testBacktestingEngine() {
        String symbol = "BTCUSDT";
        List<Candle> candles = ingestionService.getHistoricalData(symbol, TimeFrame.D1, 100);
        BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);

        BacktestResult result = backtestingEngine.runRsiSmaStrategy(series);

        assertNotNull(result);
        assertEquals(symbol, result.getSymbol());
        assertNotNull(result.getStrategyName());
        assertNotNull(result.getTradeLogs());
    }

    @Test
    @DisplayName("4. 하이브리드 의사결정 융합 리포트 생성 테스트")
    void testIntegratedDecisionReport() {
        String symbol = "BTCUSDT";
        IntegratedDecisionReport report = aggregatorService.generateDecisionReport(symbol, TimeFrame.D1, 50);

        assertNotNull(report);
        assertEquals(symbol, report.getSymbol());
        assertNotNull(report.getFinalAction());
        assertNotNull(report.getDecisionReason());
        assertNotNull(report.getQuantSignal());
        assertNotNull(report.getQualInsight());
        assertTrue(report.getTotalScore() >= -1.0 && report.getTotalScore() <= 1.0);
    }
}
