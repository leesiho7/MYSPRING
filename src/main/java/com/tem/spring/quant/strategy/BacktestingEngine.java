package com.tem.spring.quant.strategy;

import com.tem.spring.core.model.BacktestResult;
import com.tem.spring.quant.dto.CustomStrategyRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.*;
import org.ta4j.core.criteria.MaximumDrawdownCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.rules.*;

import java.util.ArrayList;
import java.util.List;

/**
 * ta4j 기반 레고 블록 스타일 커스텀 백테스팅 시뮬레이션 엔진
 */
@Slf4j
@Component
public class BacktestingEngine {

    /**
     * 1. 기본 RSI + SMA 전략 백테스팅
     */
    public BacktestResult runRsiSmaStrategy(BarSeries series) {
        CustomStrategyRequest defaultReq = CustomStrategyRequest.builder()
                .strategyName("RSI(14) + SMA(50) Trend Following")
                .useRsiEntry(true)
                .rsiBuyThreshold(30)
                .useSmaCrossEntry(true)
                .smaShortPeriod(20)
                .smaLongPeriod(50)
                .useRsiExit(true)
                .rsiSellThreshold(70)
                .useSmaCrossExit(true)
                .useStopLoss(true)
                .stopLossPct(3.0)
                .build();
        return runCustomStrategy(series, defaultReq);
    }

    /**
     * 2. [레고 블록] 사용자 맞춤 커스텀 전략 동적 조합 백테스팅
     */
    public BacktestResult runCustomStrategy(BarSeries series, CustomStrategyRequest req) {
        int barCount = series.getBarCount();
        if (barCount < 30) {
            return BacktestResult.builder()
                    .symbol(series.getName())
                    .strategyName(req.getStrategyName())
                    .tradeLogs(List.of("데이터 부족으로 백테스팅 실패 (최소 30개 이상의 캔들이 필요합니다)"))
                    .build();
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // ──────────────────────────────────────────
        // 🧱 진입 규칙(Entry Rules) 레고 블록 조립
        // ──────────────────────────────────────────
        List<Rule> entryRules = new ArrayList<>();

        if (req.isUseRsiEntry()) {
            RSIIndicator rsi = new RSIIndicator(closePrice, req.getRsiPeriod());
            entryRules.add(new CrossedUpIndicatorRule(rsi, req.getRsiBuyThreshold()));
        }

        if (req.isUseSmaCrossEntry()) {
            int shortP = Math.min(req.getSmaShortPeriod(), barCount - 1);
            int longP = Math.min(req.getSmaLongPeriod(), barCount - 1);
            SMAIndicator smaShort = new SMAIndicator(closePrice, shortP);
            SMAIndicator smaLong = new SMAIndicator(closePrice, longP);
            entryRules.add(new OverIndicatorRule(smaShort, smaLong));
        }

        if (req.isUseBollingerLowerEntry()) {
            int bPeriod = Math.min(req.getBollingerPeriod(), barCount - 1);
            SMAIndicator smaB = new SMAIndicator(closePrice, bPeriod);
            StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, bPeriod);
            BollingerBandsMiddleIndicator bbMid = new BollingerBandsMiddleIndicator(smaB);
            BollingerBandsLowerIndicator bbLow = new BollingerBandsLowerIndicator(bbMid, sd);
            entryRules.add(new UnderIndicatorRule(closePrice, bbLow));
        }

        if (entryRules.isEmpty()) {
            RSIIndicator rsi = new RSIIndicator(closePrice, 14);
            entryRules.add(new CrossedUpIndicatorRule(rsi, 30));
        }

        Rule combinedEntryRule = combineRules(entryRules, req.getEntryLogicOp());

        // ──────────────────────────────────────────
        // 🧱 청산 규칙(Exit Rules) 레고 블록 조립
        // ──────────────────────────────────────────
        List<Rule> exitRules = new ArrayList<>();

        if (req.isUseRsiExit()) {
            RSIIndicator rsi = new RSIIndicator(closePrice, req.getRsiPeriod());
            exitRules.add(new CrossedDownIndicatorRule(rsi, req.getRsiSellThreshold()));
        }

        if (req.isUseSmaCrossExit()) {
            int shortP = Math.min(req.getSmaShortPeriod(), barCount - 1);
            int longP = Math.min(req.getSmaLongPeriod(), barCount - 1);
            SMAIndicator smaShort = new SMAIndicator(closePrice, shortP);
            SMAIndicator smaLong = new SMAIndicator(closePrice, longP);
            exitRules.add(new UnderIndicatorRule(smaShort, smaLong));
        }

        if (req.isUseBollingerUpperExit()) {
            int bPeriod = Math.min(req.getBollingerPeriod(), barCount - 1);
            SMAIndicator smaB = new SMAIndicator(closePrice, bPeriod);
            StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, bPeriod);
            BollingerBandsMiddleIndicator bbMid = new BollingerBandsMiddleIndicator(smaB);
            BollingerBandsUpperIndicator bbUp = new BollingerBandsUpperIndicator(bbMid, sd);
            exitRules.add(new OverIndicatorRule(closePrice, bbUp));
        }

        if (req.isUseStopLoss()) {
            exitRules.add(new StopLossRule(closePrice, DecimalNum.valueOf(req.getStopLossPct())));
        }

        if (req.isUseTakeProfit()) {
            exitRules.add(new StopGainRule(closePrice, DecimalNum.valueOf(req.getTakeProfitPct())));
        }

        if (exitRules.isEmpty()) {
            RSIIndicator rsi = new RSIIndicator(closePrice, 14);
            exitRules.add(new CrossedDownIndicatorRule(rsi, 70));
        }

        Rule combinedExitRule = combineRules(exitRules, req.getExitLogicOp());

        Strategy strategy = new BaseStrategy(req.getStrategyName(), combinedEntryRule, combinedExitRule);

        return executeSimulation(series, strategy, req.getStrategyName());
    }

    private BacktestResult executeSimulation(BarSeries series, Strategy strategy, String strategyName) {
        TradingRecord tradingRecord = new BaseTradingRecord();
        int barCount = series.getBarCount();

        for (int i = 0; i < barCount; i++) {
            if (strategy.shouldEnter(i, tradingRecord)) {
                tradingRecord.enter(i, series.getBar(i).getClosePrice(), DecimalNum.valueOf(1));
            } else if (strategy.shouldExit(i, tradingRecord)) {
                tradingRecord.exit(i, series.getBar(i).getClosePrice(), DecimalNum.valueOf(1));
            }
        }

        List<Position> positions = tradingRecord.getPositions();
        int totalPositions = positions.size();

        int winningTrades = 0;
        double cumulativeReturn = 1.0;
        List<String> logs = new ArrayList<>();

        for (Position pos : positions) {
            double grossReturn = pos.getGrossReturn().doubleValue();
            cumulativeReturn *= grossReturn;
            double profitPct = (grossReturn - 1.0) * 100.0;

            if (profitPct > 0) {
                winningTrades++;
            }

            logs.add(String.format("진입 Bar: %d (진입가: %.2f) -> 청산 Bar: %d (청산가: %.2f), 손익: %+.2f%%",
                    pos.getEntry().getIndex(),
                    pos.getEntry().getNetPrice().doubleValue(),
                    pos.getExit().getIndex(),
                    pos.getExit().getNetPrice().doubleValue(),
                    profitPct));
        }

        int losingTrades = totalPositions - winningTrades;
        double winRate = totalPositions > 0 ? ((double) winningTrades / totalPositions) * 100.0 : 0.0;
        double totalReturnPct = (cumulativeReturn - 1.0) * 100.0;

        MaximumDrawdownCriterion mddCriterion = new MaximumDrawdownCriterion();
        double mddPct = totalPositions > 0 ? mddCriterion.calculate(series, tradingRecord).doubleValue() * 100.0 : 0.0;

        double profitFactor = calculateProfitFactor(positions);

        return BacktestResult.builder()
                .symbol(series.getName())
                .strategyName(strategyName)
                .totalTrades(totalPositions)
                .winningTrades(winningTrades)
                .losingTrades(losingTrades)
                .winRatePercentage(Math.round(winRate * 100.0) / 100.0)
                .grossReturnPercentage(Math.round(totalReturnPct * 100.0) / 100.0)
                .maxDrawdownPercentage(Math.round(mddPct * 100.0) / 100.0)
                .profitFactor(Math.round(profitFactor * 100.0) / 100.0)
                .tradeLogs(logs)
                .build();
    }

    private Rule combineRules(List<Rule> rules, String op) {
        if (rules.isEmpty()) return null;
        Rule result = rules.get(0);
        boolean isAnd = "AND".equalsIgnoreCase(op);
        for (int i = 1; i < rules.size(); i++) {
            result = isAnd ? result.and(rules.get(i)) : result.or(rules.get(i));
        }
        return result;
    }

    private double calculateProfitFactor(List<Position> positions) {
        if (positions.isEmpty()) return 0.0;
        double grossGain = 0;
        double grossLoss = 0;

        for (Position pos : positions) {
            double profit = pos.getGrossReturn().doubleValue() - 1.0;
            if (profit > 0) {
                grossGain += profit;
            } else {
                grossLoss += Math.abs(profit);
            }
        }

        if (grossLoss == 0) return grossGain > 0 ? 3.0 : 0.0;
        return grossGain / grossLoss;
    }
}
