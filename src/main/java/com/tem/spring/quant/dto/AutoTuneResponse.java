package com.tem.spring.quant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoTuneResponse {
    private String symbol;
    private String optimizationMetric;
    private int evaluatedCount;
    private CustomStrategyRequest bestConfig;
    private double bestSharpeRatio;
    private double bestWinRate;
    private double bestProfitFactor;
    private double bestMaxDrawdown;
    private double bestTotalReturn;
    private List<AutoTuneResult> topCandidates;
    private String oneClickBotConfigJson;
    private String tuningSummary;
}