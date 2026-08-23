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
}
