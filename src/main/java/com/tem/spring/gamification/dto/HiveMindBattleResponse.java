package com.tem.spring.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HiveMindBattleResponse {
    private String symbol;
    private double aiConfidenceScore; // AI 점수 (-1.0 ~ 1.0)
    private String aiDecision;        // BULLISH, BEARISH, NEUTRAL
    private double humanBullPercentage; // 유저 군단 Bull 예측 비율 (예: 68.5%)
    private double humanBearPercentage; // 유저 군단 Bear 예측 비율 (예: 31.5%)
    private int totalHumanVotes;       // 총 투표 참여 유저 수
    private String winningSide;        // "AI_WINNING", "HUMAN_WINNING", "CONSENSUS_AGREED"
    private String battleCommentary;   // 대결 해설 코멘트
}
