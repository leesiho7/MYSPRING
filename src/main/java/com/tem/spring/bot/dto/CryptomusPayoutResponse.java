package com.tem.spring.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Cryptomus Payout 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptomusPayoutResponse {

    private int state;
    private Result result;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String uuid;
        private String amount;
        private String currency;
        private String network;
        private String address;
        private String txid;
        private String status; // process, check, paid, fail, cancel

        @JsonProperty("is_final")
        private Boolean isFinal;
    }
}
