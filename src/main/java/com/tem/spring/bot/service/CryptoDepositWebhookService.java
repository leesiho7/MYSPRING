package com.tem.spring.bot.service;

import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.bot.dto.CryptoWebhookDepositRequest;
import com.tem.spring.bot.dto.DepositProcessingResultDto;
import com.tem.spring.bot.dto.PurchaseSubscriptionRequest;
import com.tem.spring.bot.entity.BotInstanceEntity;
import com.tem.spring.bot.entity.BotLicenseTokenEntity;
import com.tem.spring.bot.repository.BotLicenseTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 1단계 ~ 4단계 통합 오케스트레이터:
 * 입금 감지 웹훅 -> SHA-256 토큰 생성 -> 가상 인스턴스 자동 프로비저닝 -> 텔레그램 공식 봇 안내
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoDepositWebhookService {

    private final UserRepository userRepository;
    private final BotLicenseTokenRepository licenseTokenRepository;
    private final LicenseTokenGeneratorService tokenGeneratorService;
    private final QuantBotProvisioningService provisioningService;
    private final TelegramOfficialBotService telegramOfficialBotService;
    private final BotSubscriptionService subscriptionService;
    private final OnChainTransactionVerifierService onChainVerifierService;

    @Value("${blockchain.webhook.secret:aether_super_secret_webhook_key_2026}")
    private String webhookSecret;

    @Value("${blockchain.license.duration-days:30}")
    private int durationDays;

    /**
     * 웹훅 서명/비밀키 검증
     */
    public boolean verifyWebhookSecret(String secretHeader) {
        if (secretHeader == null || secretHeader.isBlank()) return false;
        return webhookSecret.equals(secretHeader.trim());
    }

    /**
     * 메인 파이프라인: 온체인 입금 웹훅 처리 및 24H 봇 인스턴스 자동 활성화
     */
    @Transactional
    public DepositProcessingResultDto processDepositWebhook(CryptoWebhookDepositRequest req) {
        log.info("[CryptoDepositWebhook] 🚀 Incoming Webhook: TxHash={}, Address={}, Amount={} {}, UserID={}",
                req.getTxHash(), req.getDepositAddress(), req.getAmount(), req.getCurrency(), req.getUserId());

        if (req.getTxHash() == null || req.getTxHash().trim().isBlank()) {
            throw new IllegalArgumentException("트랜잭션 해시(TxHash)가 누락되었습니다. 블록체인 전송 후 TxHash를 입력해 주세요.");
        }

        // 0. 실시간 온체인 트랜잭션 무결성 검증
        String targetNetwork = req.getNetwork() != null ? req.getNetwork() : "POLYGON";
        String expectedAddress = req.getDepositAddress() != null ? req.getDepositAddress() : "0xb0390a087488E304cA32996532Ab9f40028511fE";

        OnChainTransactionVerifierService.VerificationResult verification = onChainVerifierService.verifyTransaction(
                req.getTxHash(),
                targetNetwork,
                req.getAmount() > 0 ? req.getAmount() : 7.0,
                expectedAddress
        );

        if (!verification.isValid()) {
            log.warn("[CryptoDepositWebhook] ❌ Tx Verification Rejected for TxHash '{}': {}", req.getTxHash(), verification.getMessage());
            throw new IllegalArgumentException(verification.getMessage());
        }

        // 1. 멱등성(Idempotency) 검사: 동일 TxHash 중복 처리 방지
        Optional<BotLicenseTokenEntity> existingTokenOpt = licenseTokenRepository.findByPaymentTxHash(req.getTxHash());
        if (existingTokenOpt.isPresent()) {
            BotLicenseTokenEntity existing = existingTokenOpt.get();
            log.warn("[CryptoDepositWebhook] Duplicate TxHash '{}' detected. Returning existing license token.", req.getTxHash());
            return buildResultDto(existing, "이미 처리 완료된 입금 트랜잭션입니다.");
        }

        // 2. 유저 식별 (고유 입금 주소 매핑 or 명시적 userId)
        UserEntity user = resolveUser(req);

        // 3. 2단계: SHA-256 라이선스 토큰 자동 생성
        long nowTimestamp = System.currentTimeMillis();
        String tokenString = tokenGeneratorService.generateLicenseToken(user.getId(), req.getTxHash(), nowTimestamp);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiredAt = now.plusDays(durationDays);

        BotLicenseTokenEntity licenseToken = BotLicenseTokenEntity.builder()
                .tokenString(tokenString)
                .user(user)
                .paymentTxHash(req.getTxHash())
                .paymentNetwork(req.getNetwork() != null ? req.getNetwork() : "TRC20")
                .amountUsdt(req.getAmount() > 0 ? req.getAmount() : 7.0)
                .depositAddress(req.getDepositAddress())
                .isActive(true)
                .telegramChatId(user.getTelegramChatId())
                .startDate(now)
                .expiredAt(expiredAt)
                .createdAt(now)
                .build();

        BotLicenseTokenEntity savedToken = licenseTokenRepository.save(licenseToken);

        // 3단계 부가: 기존 구독 엔티티($7.0 USDT 먼슬리)도 함께 동기화
        subscriptionService.purchaseSubscription(PurchaseSubscriptionRequest.builder()
                .userId(user.getId())
                .amountUsdt(savedToken.getAmountUsdt())
                .paymentNetwork(savedToken.getPaymentNetwork())
                .txHash(savedToken.getPaymentTxHash())
                .build());

        // 4. 3단계: 24시간 가상 인스턴스 및 퀀트 파이썬 봇 자동 생성 (Docker/Sandbox)
        BotInstanceEntity botInstance = provisioningService.provisionAndStartBot(
                user,
                savedToken,
                req.getBotName(),
                req.getTradeSymbol(),
                req.getTimeFrame()
        );

        savedToken.setAssignedInstanceId(botInstance.getId());
        savedToken.setContainerId(botInstance.getExecutionHandle());
        licenseTokenRepository.save(savedToken);

        // 5. 4단계: 텔레그램 연동 딥링크 생성 및 기연동 유저 즉시 알림 발송
        String deepLink = telegramOfficialBotService.generateDeepLink(tokenString);

        if (user.getTelegramChatId() != null && !user.getTelegramChatId().isBlank()) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String alertMsg = String.format(
                    "🎉 *입금이 성공적으로 확인되었습니다!*\n\n" +
                    "🔑 *발급된 SHA-256 토큰:*\n`%s`\n\n" +
                    "🤖 *24H 퀀트 알림 봇 인스턴스가 활성화되었습니다.*\n" +
                    "• 가동 종목: *%s*\n" +
                    "• 이용 만료일: *%s* 까지 (30일)\n\n" +
                    "매매 체결 알림이 1:1로 실시간 전송됩니다.",
                    tokenString,
                    botInstance.getSymbol(),
                    expiredAt.format(dtf)
            );
            telegramOfficialBotService.sendMessage(user.getTelegramChatId(), alertMsg, null);
        }

        log.info("[CryptoDepositWebhook] ✅ Successfully processed deposit for user {}. Token: {}...",
                user.getUsername(), tokenString.substring(0, 16));

        return buildResultDto(savedToken, "입금이 성공적으로 확인되었으며, 24시간 퀀트 봇 인스턴스가 활성화되었습니다!");
    }

    /**
     * 유저 식별 헬퍼
     */
    private UserEntity resolveUser(CryptoWebhookDepositRequest req) {
        if (req.getUserId() != null) {
            return userRepository.findById(req.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 유저 ID입니다: " + req.getUserId()));
        }

        if (req.getDepositAddress() != null && !req.getDepositAddress().isBlank()) {
            return userRepository.findByDepositAddress(req.getDepositAddress().trim())
                    .orElseThrow(() -> new IllegalArgumentException("입금 지갑 주소에 매핑된 유저가 없습니다: " + req.getDepositAddress()));
        }

        // 테스트/시뮬레이션 환경 폴백: 첫 번째 유저 반환
        return userRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("등록된 유저가 존재하지 않습니다. 먼저 회원가입을 진행해 주세요."));
    }

    private DepositProcessingResultDto buildResultDto(BotLicenseTokenEntity token, String message) {
        long remainingDays = Math.max(0, Duration.between(LocalDateTime.now(), token.getExpiredAt()).toDays());
        String deepLink = telegramOfficialBotService.generateDeepLink(token.getTokenString());

        return DepositProcessingResultDto.builder()
                .success(true)
                .message(message)
                .licenseToken(token.getTokenString())
                .userId(token.getUser().getId())
                .username(token.getUser().getUsername())
                .depositAddress(token.getDepositAddress())
                .txHash(token.getPaymentTxHash())
                .network(token.getPaymentNetwork())
                .amountUsdt(token.getAmountUsdt())
                .instanceId(token.getAssignedInstanceId())
                .containerId(token.getContainerId())
                .instanceStatus(token.isActive() ? "RUNNING" : "STOPPED")
                .telegramDeepLink(deepLink)
                .telegramBotUsername(telegramOfficialBotService.getBotUsername())
                .telegramLinked(token.getTelegramChatId() != null && !token.getTelegramChatId().isBlank())
                .startDate(token.getStartDate())
                .expiredAt(token.getExpiredAt())
                .remainingDays(remainingDays)
                .build();
    }
}
