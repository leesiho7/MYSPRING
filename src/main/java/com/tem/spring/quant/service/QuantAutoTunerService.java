package com.tem.spring.quant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tem.spring.core.model.BacktestResult;
import com.tem.spring.core.model.Candle;
import com.tem.spring.core.model.TimeFrame;
import com.tem.spring.ingestion.service.MarketDataIngestionService;
import com.tem.spring.quant.adapter.BarSeriesMapper;
import com.tem.spring.quant.dto.AutoTuneRequest;
import com.tem.spring.quant.dto.AutoTuneResponse;
import com.tem.spring.quant.dto.AutoTuneResult;
import com.tem.spring.quant.dto.CustomStrategyRequest;
import com.tem.spring.quant.strategy.BacktestingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuantAutoTunerService {

    private final MarketDataIngestionService ingestionService;
    private final BarSeriesMapper barSeriesMapper;
    private final BacktestingEngine backtestingEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AutoTuneResponse tuneStrategy(AutoTuneRequest req) {
        String symbol = req.getSymbol() != null ? req.getSymbol().toUpperCase() : "BTCUSDT";
        TimeFrame timeFrame = req.getTimeFrame() != null ? req.getTimeFrame() : TimeFrame.D1;
        int limit = req.getCandleLimit() > 0 ? req.getCandleLimit() : 150;
        String metric = req.getOptimizationMetric() != null ? req.getOptimizationMetric().toUpperCase() : "SHARPE";

        log.info("[QuantAutoTunerService] Starting Auto-Tuning grid simulation for {} ({}) - Metric: {}", symbol, timeFrame, metric);

        List<Candle> candles = ingestionService.getHistoricalData(symbol, timeFrame, limit);
        BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);

        List<CustomStrategyRequest> candidates = generateCandidateGrid(symbol, timeFrame, limit);
        List<AutoTuneResult> results = new ArrayList<>();

        for (CustomStrategyRequest candidate : candidates) {
            BacktestResult bt = backtestingEngine.runCustomStrategy(series, candidate);
            if (bt == null || bt.getTotalTrades() == 0) continue;

            double winRate = bt.getWinRatePercentage();
            double mdd = bt.getMaxDrawdownPercentage();
            double grossReturn = bt.getGrossReturnPercentage();
            double profitFactor = bt.getProfitFactor();

            double safeMdd = Math.max(1.0, mdd);
            double sharpe = (grossReturn / safeMdd) * Math.min(2.5, Math.sqrt(Math.max(1, bt.getTotalTrades())) / 2.0);
            sharpe = Math.round(sharpe * 100.0) / 100.0;

            String label = String.format("RSI(%d,%d/%d) + SL(%.1f%%) + TP(%.1f%%)",
                    candidate.getRsiPeriod(),
                    (int) candidate.getRsiBuyThreshold(),
                    (int) candidate.getRsiSellThreshold(),
                    candidate.getStopLossPct(),
                    candidate.getTakeProfitPct());

            results.add(AutoTuneResult.builder()
                    .label(label)
                    .config(candidate)
                    .backtest(bt)
                    .sharpeRatio(sharpe)
                    .winRate(winRate)
                    .maxDrawdown(mdd)
                    .profitFactor(profitFactor)
                    .grossReturn(grossReturn)
                    .totalTrades(bt.getTotalTrades())
                    .build());
        }

        Comparator<AutoTuneResult> comparator;
        if ("WIN_RATE".equals(metric)) {
            comparator = Comparator.comparingDouble(AutoTuneResult::getWinRate).reversed();
        } else if ("PROFIT_FACTOR".equals(metric)) {
            comparator = Comparator.comparingDouble(AutoTuneResult::getProfitFactor).reversed();
        } else {
            comparator = Comparator.comparingDouble(AutoTuneResult::getSharpeRatio).reversed();
        }
        results.sort(comparator);

        for (int i = 0; i < results.size(); i++) {
            results.get(i).setRank(i + 1);
        }

        AutoTuneResult best = results.isEmpty() ? null : results.get(0);
        List<AutoTuneResult> topCandidates = results.stream().limit(5).toList();

        String oneClickJson = "{}";
        if (best != null) {
            try {
                oneClickJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(best.getConfig());
            } catch (Exception ignored) {}
        }

        String summary = best != null
                ? String.format("총 %d개 조합 시뮬레이션 완료. 최적 세팅: [%s] -> 샤프 지수 %.2f, 승률 %.1f%%, 손익비 %.2f, MDD %.1f%% 기록",
                results.size(), best.getLabel(), best.getSharpeRatio(), best.getWinRate(), best.getProfitFactor(), best.getMaxDrawdown())
                : "충분한 거래 데이터가 없어 오토튜닝을 완료할 수 없습니다.";

        return AutoTuneResponse.builder()
                .symbol(symbol)
                .optimizationMetric(metric)
                .evaluatedCount(results.size())
                .bestConfig(best != null ? best.getConfig() : null)
                .bestSharpeRatio(best != null ? best.getSharpeRatio() : 0.0)
                .bestWinRate(best != null ? best.getWinRate() : 0.0)
                .bestProfitFactor(best != null ? best.getProfitFactor() : 0.0)
                .bestMaxDrawdown(best != null ? best.getMaxDrawdown() : 0.0)
                .bestTotalReturn(best != null ? best.getGrossReturn() : 0.0)
                .topCandidates(topCandidates)
                .oneClickBotConfigJson(oneClickJson)
                .tuningSummary(summary)
                .build();
    }

    private List<CustomStrategyRequest> generateCandidateGrid(String symbol, TimeFrame tf, int limit) {
        List<CustomStrategyRequest> list = new ArrayList<>();
        int[] rsiPeriods = {9, 14, 21};
        double[] rsiBuys = {25.0, 30.0, 35.0};
        double[] rsiSells = {65.0, 70.0, 75.0};
        double[] stopLosses = {2.0, 3.0, 4.5};
        double[] takeProfits = {4.0, 6.0, 8.0};

        int count = 0;
        for (int rPeriod : rsiPeriods) {
            for (double rBuy : rsiBuys) {
                for (double rSell : rsiSells) {
                    if (rBuy >= rSell) continue;
                    for (double sl : stopLosses) {
                        for (double tp : takeProfits) {
                            count++;
                            if (count % 3 == 0 || list.size() < 16) {
                                boolean useBB = (count % 2 == 0);
                                list.add(CustomStrategyRequest.builder()
                                        .symbol(symbol)
                                        .timeFrame(tf)
                                        .limit(limit)
                                        .strategyName(String.format("AutoTuned-RSI%d-SL%.1f-TP%.1f", rPeriod, sl, tp))
                                        .useRsiEntry(true)
                                        .rsiPeriod(rPeriod)
                                        .rsiBuyThreshold(rBuy)
                                        .useSmaCrossEntry(true)
                                        .smaShortPeriod(20)
                                        .smaLongPeriod(50)
                                        .useBollingerLowerEntry(useBB)
                                        .bollingerPeriod(20)
                                        .entryLogicOp("OR")
                                        .useRsiExit(true)
                                        .rsiSellThreshold(rSell)
                                        .useSmaCrossExit(false)
                                        .useBollingerUpperExit(useBB)
                                        .useStopLoss(true)
                                        .stopLossPct(sl)
                                        .useTakeProfit(true)
                                        .takeProfitPct(tp)
                                        .exitLogicOp("OR")
                                        .build());
                            }
                            if (list.size() >= 24) return list;
                        }
                    }
                }
            }
        }
        return list;
    }
}