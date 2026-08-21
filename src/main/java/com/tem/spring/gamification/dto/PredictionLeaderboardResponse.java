package com.tem.spring.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionLeaderboardResponse {
    private int rank;
    private Long userId;
    private String nickname;
    private String walletAddress;
    private String tier; // ORACLE, GRAND_MASTER, MASTER, TRADER, NOVICE
    private int currentStreak; // 현재 연승 🔥
    private int maxStreak; // 최장 연승
    private double winRatePct; // 승률
    private int totalPredictions;
    private int wonPredictions;
    private double totalEarnedTokens;
}
