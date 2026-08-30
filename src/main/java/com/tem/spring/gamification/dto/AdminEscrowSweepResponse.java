package com.tem.spring.gamification.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 관리자 에스크로 회수 결과 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminEscrowSweepResponse {
    private boolean success;
    private String message;
    private double sweptAmount;
    private double remainingBalance;
    private String destinationAddress;
    private String network;
    private String txHash;
    private LocalDateTime sweptAt;
}