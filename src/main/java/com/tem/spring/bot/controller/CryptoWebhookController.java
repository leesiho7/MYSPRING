package com.tem.spring.bot.controller;

import com.tem.spring.bot.dto.CryptoWebhookDepositRequest;
import com.tem.spring.bot.dto.DepositProcessingResultDto;
import com.tem.spring.bot.dto.LicenseTokenInfoResponse;
import com.tem.spring.bot.entity.BotLicenseTokenEntity;
import com.tem.spring.bot.repository.BotLicenseTokenRepository;
import com.tem.spring.bot.service.CryptoDepositWebhookService;
import com.tem.spring.bot.service.TelegramOfficialBotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 1단계: Tatum / QuickNode / Alchemy 블록체인 웹훅 & 입금 감지 REST API 컨트롤러
 */
@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CryptoWebhookController {

    private final CryptoDepositWebhookService depositWebhookService;
    private final BotLicenseTokenRepository licenseTokenRepository;
    private final TelegramOfficialBotService telegramOfficialBotService;

    /**
     * 1. 외부 블록체인 인프라(Tatum, QuickNode, Alchemy) 실시간 입금 감지 웹훅 API
     */
    @PostMapping("/webhooks/crypto-deposit")
    public ResponseEntity<?> handleCryptoDepositWebhook(
            @RequestHeader(value = "x-webhook-secret", required = false) String webhookSecret,
            @Valid @RequestBody CryptoWebhookDepositRequest request) {

        log.info("[CryptoWebhookController] Received on-chain deposit webhook: txHash={}", request.getTxHash());

        // 시크릿 헤더가 전달된 경우 검증 (선택적)
        if (webhookSecret != null && !depositWebhookService.verifyWebhookSecret(webhookSecret)) {
            log.warn("[CryptoWebhookController] Unauthorized webhook request: Invalid secret key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook secret key");
        }

        DepositProcessingResultDto result = depositWebhookService.processDepositWebhook(request);
        return ResponseEntity.ok(result);
    }

    /**
     * 2. [개발/테스트/UI 연동용] 크립토 입금 즉시 시뮬레이션 API
     */
    @PostMapping("/payments/crypto/simulate-deposit")
    public ResponseEntity<DepositProcessingResultDto> simulateCryptoDeposit(
            @Valid @RequestBody CryptoWebhookDepositRequest request) {

        log.info("[CryptoWebhookController] Simulating crypto deposit for user ID: {}", request.getUserId());
        request.setProvider("SIMULATION");
        DepositProcessingResultDto result = depositWebhookService.processDepositWebhook(request);
        return ResponseEntity.ok(result);
    }

    /**
     * 3. 유저의 현재 활성화된 라이선스 토큰 및 텔레그램 연동 딥링크 조회 API
     */
    @GetMapping("/payments/license/{userId}")
    public ResponseEntity<LicenseTokenInfoResponse> getUserLicenseToken(@PathVariable Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<BotLicenseTokenEntity> tokens = licenseTokenRepository.findActiveTokensByUserId(userId, now);

        if (tokens.isEmpty()) {
            return ResponseEntity.ok(LicenseTokenInfoResponse.builder()
                    .success(false)
                    .message("활성화된 퀀트 봇 라이선스 토큰이 없습니다. ($7 USDT 입금 시 자동 발급)")
                    .userId(userId)
                    .isActive(false)
                    .remainingDays(0)
                    .build());
        }

        BotLicenseTokenEntity activeToken = tokens.get(0);
        long remainingDays = Math.max(0, Duration.between(now, activeToken.getExpiredAt()).toDays());
        String deepLink = telegramOfficialBotService.generateDeepLink(activeToken.getTokenString());
        boolean isLinked = activeToken.getTelegramChatId() != null && !activeToken.getTelegramChatId().isBlank();

        return ResponseEntity.ok(LicenseTokenInfoResponse.builder()
                .success(true)
                .message("활성 라이선스 토큰 조회 성공")
                .tokenId(activeToken.getId())
                .tokenString(activeToken.getTokenString())
                .userId(userId)
                .username(activeToken.getUser().getUsername())
                .paymentTxHash(activeToken.getPaymentTxHash())
                .paymentNetwork(activeToken.getPaymentNetwork())
                .amountUsdt(activeToken.getAmountUsdt())
                .isActive(true)
                .telegramChatId(activeToken.getTelegramChatId())
                .telegramLinked(isLinked)
                .telegramDeepLink(deepLink)
                .assignedInstanceId(activeToken.getAssignedInstanceId())
                .containerId(activeToken.getContainerId())
                .startDate(activeToken.getStartDate())
                .expiredAt(activeToken.getExpiredAt())
                .remainingDays(remainingDays)
                .build());
    }

    /**
     * 4. 라이선스 토큰 유효성 즉시 검증 API (파이썬 봇 또는 외부 인스턴스에서 호출)
     */
    @GetMapping("/payments/license/verify")
    public ResponseEntity<?> verifyLicenseToken(@RequestParam String token) {
        Optional<BotLicenseTokenEntity> tokenOpt = licenseTokenRepository.findByTokenString(token);
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("INVALID_OR_EXPIRED_TOKEN");
        }

        BotLicenseTokenEntity valid = tokenOpt.get();
        return ResponseEntity.ok(java.util.Map.of(
                "valid", true,
                "userId", valid.getUser().getId(),
                "username", valid.getUser().getUsername(),
                "telegramChatId", valid.getTelegramChatId() != null ? valid.getTelegramChatId() : "",
                "expiredAt", valid.getExpiredAt().toString()
        ));
    }

    /**
     * 5. [순수 온체인 P2P] 네트워크별 공식 입금 지갑 주소 및 안내 조회 API
     */
    @GetMapping("/payments/deposit-wallets")
    public ResponseEntity<?> getDepositWallets() {
        return ResponseEntity.ok(java.util.Map.of(
                "amountUsdt", 7.0,
                "currency", "USDT",
                "wallets", java.util.Map.of(
                        "polygon", "0x71C8364f3B80430C4361b17b2F3057173b0638A9",
                        "bsc", "0x71C8364f3B80430C4361b17b2F3057173b0638A9",
                        "trc20", "TYDzsYUE282QJ84qjxoKqT5wD3ZgK8ZABC",
                        "solana", "7Xv9BfV4U932pQZ9USDT4444444444444444444444444444"
                ),
                "notice", "입금 전송 시 온체인 트랜잭션이 블록체인에서 승인되는 즉시(1~2분 내) 24시간 봇이 자동 활성화됩니다."
        ));
    }
}
