package com.tem.spring.bot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Tatum, QuickNode, Alchemy 등 블록체인 인프라 웹훅 및 입금 시뮬레이션 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CryptoWebhookDepositRequest {

    // 유저 식별용 (지갑 주소 매핑 또는 명시적 userId)
    private Long userId;

    // 결제된 입금 지갑 주소
    private String depositAddress;

    // 온체인 트랜잭션 해시 (TxHash)
    @NotBlank(message = "트랜잭션 해시는 필수입니다.")
    private String txHash;

    // 입금된 코인/토큰 심볼 (USDT, USDC, TRX, SOL, MATIC 등)
    @Builder.Default
    private String currency = "USDT";

    // 입금된 금액 (기본 $7.0)
    @Builder.Default
    private double amount = 7.0;

    // 블록체인 네트워크 (TRC20, POLYGON, BSC, SOLANA, ARBITRUM)
    @Builder.Default
    private String network = "TRC20";

    // 블록체인 승인 수 (Confirmations)
    @Builder.Default
    private int confirmations = 1;

    // 웹훅 공급자 식별 (TATUM, QUICKNODE, ALCHEMY, SIMULATION)
    @Builder.Default
    private String provider = "TATUM";

    // 추가 메타데이터 (봇 이름, 심볼 등)
    private String botName;
    private String tradeSymbol;
    private String timeFrame;
}
