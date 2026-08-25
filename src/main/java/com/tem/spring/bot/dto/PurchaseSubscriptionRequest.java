package com.tem.spring.bot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseSubscriptionRequest {

    @NotNull(message = "유저 ID는 필수입니다.")
    private Long userId;

    @Builder.Default
    private String paymentNetwork = "TRC20"; // TRC20, POLYGON, BSC, ARBITRUM, SOLANA

    private String txHash; // 온체인 테더 결제 트랜잭션 해시 (테스트 시 자동 생성 가능)

    @Builder.Default
    private double amountUsdt = 7.0; // 월 $7.0 USDT
}
