package com.tem.spring.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Cryptomus 10연승 $10 보상 Payout(자동 출금) 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CryptomusPayoutRequest {

    @JsonProperty("amount")
    @Builder.Default
    private String amount = "10.00";

    @JsonProperty("currency")
    @Builder.Default
    private String currency = "USDT";

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("address")
    private String address; // 유저의 수신 지갑 주소

    @JsonProperty("network")
    @Builder.Default
    private String network = "polygon"; // polygon, bsc, tron, sol

    @JsonProperty("url_callback")
    private String urlCallback;

    @JsonProperty("is_subtract")
    @Builder.Default
    private String isSubtract = "0"; // 0: 수수료 상점 부담, 1: 유저 수수료 차감
}
