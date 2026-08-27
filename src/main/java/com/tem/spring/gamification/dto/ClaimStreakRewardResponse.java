package com.tem.spring.gamification.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 10연승 $10 USDT 보상 Claim 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimStreakRewardResponse {

    private boolean success;
    private String message;

    private Long userId;
    private String nickname;
    private int currentStreak;
    private double rewardAmountUsdt;

    private String destinationAddress;
    private String network;
    private String payoutUuid;
    private String txHash;
    private String status; // PROCESSING, COMPLETED, FAILED

    private LocalDateTime claimedAt;
}
