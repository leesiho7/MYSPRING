package com.tem.spring.gamification.service;

import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.bot.dto.CryptomusPayoutRequest;
import com.tem.spring.bot.dto.CryptomusPayoutResponse;
import com.tem.spring.bot.service.CryptomusClientService;
import com.tem.spring.bot.service.TelegramOfficialBotService;
import com.tem.spring.community.entity.TokenRewardLogEntity;
import com.tem.spring.community.repository.TokenRewardLogRepository;
import com.tem.spring.gamification.dto.ClaimStreakRewardRequest;
import com.tem.spring.gamification.dto.ClaimStreakRewardResponse;
import com.tem.spring.gamification.entity.UserPredictionStatsEntity;
import com.tem.spring.gamification.repository.UserPredictionStatsRepository;
import com.tem.spring.wallet.entity.WithdrawalEntity;
import com.tem.spring.wallet.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 10연승 달성 시 $10 USDT 자동 출금(Claim) 보상 지급 서비스 (Cryptomus Payout API 연동)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreakRewardClaimService {

    private final UserPredictionStatsRepository statsRepository;
    private final UserRepository userRepository;
    private final CryptomusClientService cryptomusClientService;
    private final Web3EscrowTransferService web3EscrowTransferService;
    private final WithdrawalRepository withdrawalRepository;
    private final TokenRewardLogRepository rewardLogRepository;
    private final TelegramOfficialBotService telegramOfficialBotService;

    /**
     * 10연승 $10 USDT 보상 즉시 Claim 및 온체인 송금 처리 (Web3 직접 서명 및 전송)
     */
    @Transactional
    public ClaimStreakRewardResponse claimStreakReward(ClaimStreakRewardRequest req) {
        log.info("[StreakRewardClaimService] Processing 10-Win Streak Claim for user ID: {}, Address: {}, Network: {}",
                req.getUserId(), req.getDestinationAddress(), req.getNetwork());

        // 1. 유저 및 예측 전적 조회
        UserEntity user = userRepository.findByIdWithPessimisticLock(req.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. ID: " + req.getUserId()));

        UserPredictionStatsEntity stats = statsRepository.findByUserId(req.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저의 예측 통계 데이터를 찾을 수 없습니다."));

        // 2. 10연승 충족 여부 엄격 검증 (Anti-Fraud)
        if (stats.getCurrentStreak() < 10) {
            throw new IllegalStateException(String.format("10연승 조건을 달성하지 못했습니다. (현재 연속 적중: %d회 / 목표: 10회)",
                    stats.getCurrentStreak()));
        }

        // 3. 에스크로 풀 잔액 검증 (100 USDT 한도 초과 방지)
        com.tem.spring.gamification.dto.EscrowPoolStatusDto poolStatus = getEscrowPoolStatus();
        if (poolStatus.getCurrentBalance() < 10.0) {
            throw new IllegalStateException("100 USDT 에스크로 풀 보상 예치금이 모두 소진되었습니다. 다음 시즌 이벤트를 기대해 주세요!");
        }

        // 4. [방식 2] Web3 ERC-20 온체인 스마트 컨트랙트 직접 전송 ($10.00 USDT)
        String network = req.getNetwork() != null ? req.getNetwork().toLowerCase() : "polygon"; // 가스비 절감을 위해 Polygon 기본
        Web3EscrowTransferService.OnChainTransferResult transferResult = web3EscrowTransferService.sendOnChainTransfer(
                req.getDestinationAddress().trim(), 10.0, network);

        String txHash = transferResult.getTxHash();
        String payoutUuid = "WEB3-" + UUID.randomUUID().toString().substring(0, 8);

        LocalDateTime now = LocalDateTime.now();

        // 5. 출금 및 회계 감사 원장에 기록
        WithdrawalEntity withdrawal = WithdrawalEntity.builder()
                .user(user)
                .amount(10.0)
                .destinationType("WEB3_ERC20_" + network.toUpperCase())
                .destinationAddress(req.getDestinationAddress())
                .status("COMPLETED")
                .proofTxHash(txHash)
                .cryptographicProof("WEB3_TX_" + txHash)
                .requestedAt(now)
                .processedAt(now)
                .build();
        withdrawalRepository.save(withdrawal);

        TokenRewardLogEntity rewardLog = TokenRewardLogEntity.builder()
                .recipient(user)
                .tokenAmount(10.0)
                .reason("🔥 10연승 달성 $10.00 USDT 즉시 출금 보상")
                .txHash(txHash)
                .rewardedAt(now)
                .build();
        rewardLogRepository.save(rewardLog);

        // 6. 연승 보상 수령 후 연승 카운트 차감 (또는 유지 정책)
        // 10연승 1회 보상 수령 시 currentStreak를 0으로 리셋하여 중복 Claim 방지
        stats.setCurrentStreak(0);
        statsRepository.save(stats);

        // 7. 텔레그램 연동되어 있으면 즉시 1:1 축하 푸시 알림 발송
        if (user.getTelegramChatId() != null && !user.getTelegramChatId().isBlank()) {
            String telegramMsg = String.format(
                    "🏆 *[10연승 잭팟 달성 & $10 USDT 지급 완료!]*\n\n" +
                    "축하합니다, *%s*님! 10연승 미션을 완수하셨습니다. 🎉\n\n" +
                    "• 지급 금액: *$10.00 USDT*\n" +
                    "• 출금 네트워크: *%s*\n" +
                    "• 수신 지갑 주소: `%s`\n" +
                    "• 트랜잭션 해시: `%s`\n\n" +
                    "온체인 블록체인에서 안전하게 송금되었습니다. 계속해서 다음 연승에 도전해 보세요! 🔥",
                    user.getNickname(), network.toUpperCase(), req.getDestinationAddress(), txHash
            );
            telegramOfficialBotService.sendMessage(user.getTelegramChatId(), telegramMsg, null);
        }

        log.info("[StreakRewardClaimService] ✅ Successfully claimed $10 USDT for user '{}'. TxHash: {}",
                user.getUsername(), txHash);

        return ClaimStreakRewardResponse.builder()
                .success(true)
                .message("🎉 10연승 달성 보상 $10.00 USDT가 지갑으로 안전하게 송금되었습니다!")
                .userId(user.getId())
                .nickname(user.getNickname())
                .currentStreak(10)
                .rewardAmountUsdt(10.0)
                .destinationAddress(req.getDestinationAddress())
                .network(network)
                .payoutUuid(payoutUuid)
                .txHash(txHash)
                .status("COMPLETED")
                .claimedAt(now)
                .build();
    }

    private final java.util.concurrent.atomic.AtomicReference<Double> configuredCapacity = new java.util.concurrent.atomic.AtomicReference<>(0.0);
    private final java.util.concurrent.atomic.AtomicReference<String> poolStatus = new java.util.concurrent.atomic.AtomicReference<>("STANDBY");
    private final java.util.concurrent.atomic.AtomicReference<String> configuredEscrowAddress = new java.util.concurrent.atomic.AtomicReference<>("0xb0390a087488E304cA32996532Ab9f40028511fE");
    private final java.util.List<com.tem.spring.gamification.dto.AdminEscrowAuditLogDto> auditLogs = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 실시간 온체인 에스크로 풀 상태 및 잔여 수량 조회 (Web3 Polygon USDT 온체인 잔액 실시간 감지 연동)
     */
    @Transactional(readOnly = true)
    public com.tem.spring.gamification.dto.EscrowPoolStatusDto getEscrowPoolStatus() {
        String address = configuredEscrowAddress.get();
        double onChainBal = 0.0;
        try {
            onChainBal = web3EscrowTransferService.getOnChainUsdtBalance(address, "POLYGON");
        } catch (Exception e) {
            log.warn("[EscrowPool] Live on-chain balance query error: {}", e.getMessage());
        }

        double initialCapacity;
        double currentBalance;
        long winnersCount = rewardLogRepository.countByReasonContaining("10연승");
        int totalWinners = (int) winnersCount;
        double rewardPerWinner = 10.0;
        double claimedAmount = totalWinners * rewardPerWinner;

        if (onChainBal > 0.0) {
            // [경로 B] Polygon 메인넷 온체인 지갑 실제 입금액 실시간 자동 감지
            currentBalance = onChainBal;
            initialCapacity = onChainBal + claimedAmount;
        } else {
            // 온체인 잔액이 0일 경우 관리자 설정 예치금 사용
            initialCapacity = configuredCapacity.get();
            currentBalance = Math.max(0.0, initialCapacity - claimedAmount);
        }

        int maxWinners = (int) (initialCapacity / 10.0);
        int remainingWinners = Math.max(0, (int) (currentBalance / 10.0));
        String currentStatus = poolStatus.get();
        if ("ACTIVE".equalsIgnoreCase(currentStatus) && currentBalance <= 0) {
            currentStatus = "EXHAUSTED";
        }

        return com.tem.spring.gamification.dto.EscrowPoolStatusDto.builder()
                .poolName("1-HOUR QUICK STRIKE PREDICTION EVENT POOL")
                .initialCapacity(initialCapacity)
                .currentBalance(currentBalance)
                .claimedAmount(claimedAmount)
                .totalWinners(totalWinners)
                .maxWinners(maxWinners)
                .remainingWinners(remainingWinners)
                .rewardPerWinner(rewardPerWinner)
                .escrowAddress(address)
                .network("POLYGON")
                .status(currentStatus)
                .build();
    }

    /**
     * [관리자] 1. 에스크로 예치금 및 풀 활성화 상태 설정
     */
    public com.tem.spring.gamification.dto.EscrowPoolStatusDto updateEscrowPoolCapacity(com.tem.spring.gamification.dto.AdminEscrowConfigRequest req) {
        log.info("[AdminEscrow] Setting capacity: {}, status: {}, address: {}",
                req.getInitialCapacity(), req.getStatus(), req.getEscrowAddress());

        if (req.getInitialCapacity() != null) {
            configuredCapacity.set(req.getInitialCapacity());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            poolStatus.set(req.getStatus().toUpperCase());
        }
        if (req.getEscrowAddress() != null && !req.getEscrowAddress().isBlank()) {
            configuredEscrowAddress.set(req.getEscrowAddress());
        }

        auditLogs.add(0, com.tem.spring.gamification.dto.AdminEscrowAuditLogDto.builder()
                .type("DEPOSIT_SYNC")
                .description(String.format("관리자 풀 예치금 동기화: %.2f USDT (상태: %s)", configuredCapacity.get(), poolStatus.get()))
                .amount(configuredCapacity.get())
                .destinationAddress(configuredEscrowAddress.get())
                .network("POLYGON")
                .txHash("CONFIG_SET_" + System.currentTimeMillis())
                .status("SUCCESS")
                .timestamp(LocalDateTime.now())
                .build());

        return getEscrowPoolStatus();
    }

    /**
     * [관리자] 2. 에스크로 잔액 대표님 지갑으로 전액/일부 긴급 회수 (Sweep / Refund)
     */
    @Transactional
    public com.tem.spring.gamification.dto.AdminEscrowSweepResponse sweepEscrowFunds(com.tem.spring.gamification.dto.AdminEscrowSweepRequest req) {
        log.info("[AdminEscrow] 🚨 Processing Admin Sweep to: {}, Amount: {}, Network: {}",
                req.getDestinationAddress(), req.getAmount(), req.getNetwork());

        double initial = configuredCapacity.get();
        long winnersCount = rewardLogRepository.countByReasonContaining("10연승");
        double claimed = winnersCount * 10.0;
        double currentBalance = Math.max(0.0, initial - claimed);

        if (currentBalance <= 0) {
            throw new IllegalStateException("회수 가능한 에스크로 풀 잔액이 0.00 USDT입니다.");
        }

        double sweepAmount = (req.getAmount() != null && req.getAmount() > 0)
                ? Math.min(req.getAmount(), currentBalance)
                : currentBalance;

        String network = (req.getNetwork() != null && !req.getNetwork().isBlank()) ? req.getNetwork().toLowerCase() : "polygon";

        // [방식 2] Web3 온체인 스마트 컨트랙트 직접 서명 및 회수 송금 실행
        Web3EscrowTransferService.OnChainTransferResult transferResult = web3EscrowTransferService.sendOnChainTransfer(
                req.getDestinationAddress().trim(), sweepAmount, network);
        String txHash = transferResult.getTxHash();

        // 에스크로 용량 차감
        double newCapacity = Math.max(0.0, initial - sweepAmount);
        configuredCapacity.set(newCapacity);
        if (newCapacity <= 0) {
            poolStatus.set("STANDBY");
        }

        LocalDateTime now = LocalDateTime.now();

        // 감사 원장 기록
        auditLogs.add(0, com.tem.spring.gamification.dto.AdminEscrowAuditLogDto.builder()
                .type("ADMIN_SWEEP")
                .description(String.format("👑 대표님 지갑으로 %.2f USDT 전액/일부 회수 완료", sweepAmount))
                .amount(sweepAmount)
                .destinationAddress(req.getDestinationAddress())
                .network(network.toUpperCase())
                .txHash(txHash)
                .status("SUCCESS")
                .timestamp(now)
                .build());

        return com.tem.spring.gamification.dto.AdminEscrowSweepResponse.builder()
                .success(true)
                .message(String.format("성공적으로 %.2f USDT가 대표님 지갑(%s)으로 회수되었습니다.", sweepAmount, req.getDestinationAddress()))
                .sweptAmount(sweepAmount)
                .remainingBalance(Math.max(0.0, newCapacity - claimed))
                .destinationAddress(req.getDestinationAddress())
                .network(network.toUpperCase())
                .txHash(txHash)
                .sweptAt(now)
                .build();
    }

    /**
     * [관리자] 3. 에스크로 감사 원장 및 최근 트랜잭션 내역 조회
     */
    public java.util.List<com.tem.spring.gamification.dto.AdminEscrowAuditLogDto> getAdminAuditLogs() {
        return new java.util.ArrayList<>(auditLogs);
    }
}
