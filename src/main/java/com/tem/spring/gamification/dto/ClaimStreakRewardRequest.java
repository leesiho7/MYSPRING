package com.tem.spring.gamification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 10연승 $10 USDT 보상 Claim 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimStreakRewardRequest {

    @NotNull(message = "유저 ID는 필수입니다.")
    private Long userId;

    @NotBlank(message = "수신할 지갑 주소는 필수입니다.")
    private String destinationAddress;

    // 출금 네트워크: polygon, bsc, tron, solana (polygon/bsc 기본 권장)
    @Builder.Default
    private String network = "polygon";
}
