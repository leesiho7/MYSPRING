package com.tem.spring.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Cryptomus 결제 인보이스 생성 결과 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptomusInvoiceResponse {

    private int state; // 0: 성공
    private Result result;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String uuid;

        @JsonProperty("order_id")
        private String orderId;

        private String amount;
        private String currency;
        private String network;
        private String address;

        @JsonProperty("payment_status")
        private String paymentStatus;

        private String url; // 결제 호스팅 웹 페이지 URL

        @JsonProperty("expired_at")
        private Long expiredAt;
    }
}
