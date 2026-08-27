package com.tem.spring.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Cryptomus 결제/출금 완료 웹훅 수신 페이로드
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptomusWebhookPayload {

    private String type; // payment, payout
    private String uuid;

    @JsonProperty("order_id")
    private String orderId;

    private String amount;

    @JsonProperty("payment_amount")
    private String paymentAmount;

    @JsonProperty("payment_amount_usd")
    private String paymentAmountUsd;

    @JsonProperty("merchant_amount")
    private String merchantAmount;

    private String currency;
    private String network;
    private String address;
    private String txid; // 온체인 트랜잭션 해시

    private String status; // paid, paid_over, wrong_amount, process, cancel, check, fail

    @JsonProperty("is_final")
    private Boolean isFinal;

    @JsonProperty("additional_data")
    private String additionalData;

    private String sign; // 요청 무결성 검증용 서명 해시
}
