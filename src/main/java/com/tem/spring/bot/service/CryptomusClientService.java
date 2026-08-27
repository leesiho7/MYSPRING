package com.tem.spring.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tem.spring.bot.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

/**
 * Cryptomus Crypto Payment Gateway & Payout API 클라이언트 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryptomusClientService {

    private final CryptomusSignatureUtil signatureUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cryptomus.merchant-id:mock_merchant_uuid}")
    private String merchantId;

    @Value("${cryptomus.payment-key:mock_payment_api_key}")
    private String paymentApiKey;

    @Value("${cryptomus.payout-key:mock_payout_api_key}")
    private String payoutApiKey;

    @Value("${cryptomus.base-url:https://api.cryptomus.com}")
    private String baseUrl;

    @Value("${cryptomus.callback-url:https://api.aether-trading.com/api/v1/payments/cryptomus/webhook}")
    private String defaultCallbackUrl;

    @Value("${cryptomus.mock-mode:true}")
    private boolean mockMode;

    private final WebClient webClient = WebClient.builder().build();

    /**
     * 1. $7 USDT 봇 호스팅 결제 인보이스 생성 (POST /v1/payment)
     */
    public CryptomusInvoiceResponse createPaymentInvoice(CryptomusCreateInvoiceRequest req) {
        if (req.getOrderId() == null || req.getOrderId().isBlank()) {
            req.setOrderId("ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6));
        }
        if (req.getUrlCallback() == null) {
            req.setUrlCallback(defaultCallbackUrl);
        }

        log.info("[CryptomusClient] Creating Payment Invoice: OrderID={}, Amount={} {}, Network={}",
                req.getOrderId(), req.getAmount(), req.getCurrency(), req.getNetwork());

        // Mock 모드 또는 테스트 API 키인 경우 시뮬레이션 응답 반환
        if (mockMode || paymentApiKey.contains("mock")) {
            String mockUuid = UUID.randomUUID().toString();
            String mockAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "777";
            String mockPayUrl = "https://pay.cryptomus.com/pay/" + mockUuid;

            return CryptomusInvoiceResponse.builder()
                    .state(0)
                    .result(CryptomusInvoiceResponse.Result.builder()
                            .uuid(mockUuid)
                            .orderId(req.getOrderId())
                            .amount(req.getAmount())
                            .currency(req.getCurrency())
                            .network(req.getNetwork() != null ? req.getNetwork() : "tron")
                            .address(mockAddress)
                            .paymentStatus("check")
                            .url(mockPayUrl)
                            .expiredAt(System.currentTimeMillis() / 1000 + 3600)
                            .build())
                    .build();
        }

        try {
            String sign = signatureUtil.generateSign(req, paymentApiKey);

            return webClient.post()
                    .uri(baseUrl + "/v1/payment")
                    .header("merchant", merchantId)
                    .header("sign", sign)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(CryptomusInvoiceResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("[CryptomusClient] Failed to create payment invoice", e);
            throw new RuntimeException("Cryptomus 결제 인보이스 생성 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 2. 10연승 $10 USDT 자동 출금 (POST /v1/payout)
     */
    public CryptomusPayoutResponse createPayout(CryptomusPayoutRequest req) {
        if (req.getOrderId() == null || req.getOrderId().isBlank()) {
            req.setOrderId("PAYOUT-10WIN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6));
        }

        log.info("[CryptomusClient] 💸 Executing $10 Payout: OrderID={}, ToAddress={}, Network={}",
                req.getOrderId(), req.getAddress(), req.getNetwork());

        if (mockMode || payoutApiKey.contains("mock")) {
            String mockPayoutUuid = UUID.randomUUID().toString();
            String mockTxHash = "0x" + UUID.randomUUID().toString().replace("-", "") + "WIN10";

            return CryptomusPayoutResponse.builder()
                    .state(0)
                    .result(CryptomusPayoutResponse.Result.builder()
                            .uuid(mockPayoutUuid)
                            .amount(req.getAmount())
                            .currency(req.getCurrency())
                            .network(req.getNetwork())
                            .address(req.getAddress())
                            .txid(mockTxHash)
                            .status("paid")
                            .isFinal(true)
                            .build())
                    .build();
        }

        try {
            String sign = signatureUtil.generateSign(req, payoutApiKey);

            return webClient.post()
                    .uri(baseUrl + "/v1/payout")
                    .header("merchant", merchantId)
                    .header("sign", sign)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(CryptomusPayoutResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("[CryptomusClient] Failed to execute payout", e);
            throw new RuntimeException("Cryptomus 출금(Payout) 처리 중 오류 발생: " + e.getMessage(), e);
        }
    }

    public String getPaymentApiKey() {
        return paymentApiKey;
    }
}
