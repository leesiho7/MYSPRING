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
public class PredictionResponse {
    private Long predictionId;
    private Long userId;
    private String nickname;
    private String symbol;
    private String predictionType;
    private String predictedDirection;
    private Double predictedPrice;
    private Double entryPrice;
    private Double settledPrice;
    private String status;
    private double rewardTokens;
    private LocalDateTime targetTime;
    private LocalDateTime createdAt;
}
