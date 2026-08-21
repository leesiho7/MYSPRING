package com.tem.spring.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaStrategyResponse {
    private Long arenaId;
    private int rank;
    private Long authorId;
    private String authorNickname;
    private String authorWallet;
    private String strategyName;
    private String symbol;
    private double currentReturnPct;
    private double winRatePct;
    private double profitFactor;
    private double maxDrawdownPct;
    private int totalTrades;
    private int copyCount;
    private String season;
    private String strategyConfigJson;
    private LocalDateTime createdAt;
}
