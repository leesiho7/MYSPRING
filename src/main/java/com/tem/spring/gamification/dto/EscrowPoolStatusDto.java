package com.tem.spring.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1-Hour Quick Strike Prediction League 온체인 에스크로 보상 풀 상태 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowPoolStatusDto {
    private String poolName;
    private double initialCapacity; // 초기 예치 풀 (예: 100.0 USDT)
    private double currentBalance;   // 실시간 잔여 풀 (예: 100.0 - (10 * 당첨자수))
    private double claimedAmount;    // 총 지급된 보상액
    private int totalWinners;        // 현재까지 10연승 달성 당첨자 수
    private int maxWinners;          // 최대 보상 가능 당첨자 수 (10명)
    private int remainingWinners;    // 잔여 보상 가능 인원 (maxWinners - totalWinners)
    private double rewardPerWinner;  // 1인당 보상액 (10.0 USDT)
    private String escrowAddress;    // 투명 에스크로 지갑 주소
    private String network;          // 네트워크 (POLYGON)
    private String status;           // ACTIVE, EXHAUSTED
}
