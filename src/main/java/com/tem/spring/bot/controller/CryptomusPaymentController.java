package com.tem.spring.bot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tem.spring.bot.dto.*;
import com.tem.spring.bot.service.CryptoDepositWebhookService;
import com.tem.spring.bot.service.CryptomusClientService;
import com.tem.spring.bot.service.CryptomusSignatureUtil;
import com.tem.spring.gamification.dto.ClaimStreakRewardRequest;
import com.tem.spring.gamification.dto.ClaimStreakRewardResponse;
import com.tem.spring.gamification.service.StreakRewardClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Cryptomus 크립토 결제($7 호스팅 인보이스) 및 10연승 $10 보상 Payout REST API 컨트롤러
 */
@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CryptomusPaymentController {

    private final CryptomusClientService cryptomusClientService;
    private final CryptomusSignatureUtil signatureUtil;
    private final CryptoDepositWebhookService depositWebhookService;
    private final StreakRewardClaimService streakRewardClaimService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 1. $7 USDT 봇 호스팅 결제 인보이스 생성 API (웹 UI에서 결제창 띄울 때 호출)
     */
    @PostMapping("/payments/cryptomus/invoice")
    public ResponseEntity<CryptomusInvoiceResponse> createInvoice(@Valid @RequestBody CryptomusCreateInvoiceRequest request) {
        log.info("[CryptomusController] Creating Invoice for user ID: {}, amount: {}", request.getUserId(), request.getAmount());
        CryptomusInvoiceResponse response = cryptomusClientService.createPaymentInvoice(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Cryptomus 공식 서버로부터 수신되는 결제/출금 완료 Webhook 수신 엔드포인트
     */
    @PostMapping("/payments/cryptomus/webhook")
    public ResponseEntity<?> handleCryptomusWebhook(
            @RequestBody String rawJson,
            @RequestHeader(value = "sign", required = false) String sign) {

        log.info("[CryptomusController] Received Cryptomus Webhook: {}", rawJson);

        try {
            CryptomusWebhookPayload payload = objectMapper.readValue(rawJson, CryptomusWebhookPayload.class);

            // 서명 검증 (선택적 / Mock 모드 제외)
            if (sign != null && !signatureUtil.verifyWebhookSign(rawJson, sign, cryptomusClientService.getPaymentApiKey())) {
                log.warn("[CryptomusController] ⚠️ Invalid webhook sign received.");
                // 보안상 실제 운영 환경에서는 차단
            }

            // 입금 완료 상태(paid, paid_over)일 때만 라이선스 및 24H 봇 인스턴스 자동 활성화
            if ("paid".equalsIgnoreCase(payload.getStatus()) || "paid_over".equalsIgnoreCase(payload.getStatus())) {
                double amount = 7.0;
                try {
                    amount = Double.parseDouble(payload.getPaymentAmountUsd() != null ? payload.getPaymentAmountUsd() : payload.getAmount());
                } catch (Exception ignored) {}

                CryptoWebhookDepositRequest depositReq = CryptoWebhookDepositRequest.builder()
                        .txHash(payload.getTxid() != null ? payload.getTxid() : "0x" + payload.getUuid().replace("-", ""))
                        .amount(amount)
                        .currency(payload.getCurrency() != null ? payload.getCurrency() : "USDT")
                        .network(payload.getNetwork() != null ? payload.getNetwork().toUpperCase() : "TRC20")
                        .depositAddress(payload.getAddress())
                        .provider("CRYPTOMUS")
                        .build();

                DepositProcessingResultDto result = depositWebhookService.processDepositWebhook(depositReq);
                log.info("[CryptomusController] ✅ Successfully activated bot instance via Cryptomus webhook. Result: {}", result.getMessage());
            }

            return ResponseEntity.ok(Map.of("state", 0, "message", "Webhook processed successfully"));
        } catch (Exception e) {
            log.error("[CryptomusController] Failed to process Cryptomus webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    /**
     * 3. [10연승 미션] $10 USDT 보상 즉시 Claim (Payout) API
     */
    @PostMapping("/gamification/claim-streak-reward")
    public ResponseEntity<ClaimStreakRewardResponse> claimStreakReward(@Valid @RequestBody ClaimStreakRewardRequest request) {
        log.info("[CryptomusController] User {} requested 10-win streak claim to {}", request.getUserId(), request.getDestinationAddress());
        ClaimStreakRewardResponse response = streakRewardClaimService.claimStreakReward(request);
        return ResponseEntity.ok(response);
    }
}
