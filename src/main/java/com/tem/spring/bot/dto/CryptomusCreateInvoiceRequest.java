package com.tem.spring.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Cryptomus 결제 인보이스 생성 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CryptomusCreateInvoiceRequest {

    private Long userId;

    @JsonProperty("amount")
    @Builder.Default
    private String amount = "7.00";

    @JsonProperty("currency")
    @Builder.Default
    private String currency = "USDT";

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("network")
    private String network; // tron, polygon, bsc, sol, arbitrum

    @JsonProperty("url_callback")
    private String urlCallback;

    @JsonProperty("url_return")
    private String urlReturn;

    @JsonProperty("url_success")
    private String urlSuccess;

    @JsonProperty("is_payment_multiple")
    @Builder.Default
    private boolean isPaymentMultiple = false;

    @JsonProperty("lifetime")
    @Builder.Default
    private int lifetime = 3600; // 1시간 유효

    @JsonProperty("additional_data")
    private String additionalData;
}
