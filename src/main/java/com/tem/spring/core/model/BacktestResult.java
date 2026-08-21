package com.tem.spring.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * ta4j 백테스팅 시뮬레이션 결과
 */
@Value
@Builder
public class BacktestResult {
    String symbol;
    String strategyName;
    int totalTrades;
    int winningTrades;
    int losingTrades;
    double winRatePercentage;
    double grossReturnPercentage;
    double maxDrawdownPercentage;
    double profitFactor;
    List<String> tradeLogs;
}
