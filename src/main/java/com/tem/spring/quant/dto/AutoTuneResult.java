package com.tem.spring.quant.dto;

import com.tem.spring.core.model.BacktestResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoTuneResult {
    private int rank;
    private String label;
    private CustomStrategyRequest config;
    private BacktestResult backtest;
    private double sharpeRatio;
    private double winRate;
    private double maxDrawdown;
    private double profitFactor;
    private double grossReturn;
    private int totalTrades;
}