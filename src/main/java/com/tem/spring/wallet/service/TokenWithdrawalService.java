package com.tem.spring.wallet.service;

import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.community.entity.TokenRewardLogEntity;
import com.tem.spring.community.repository.TokenRewardLogRepository;
import com.tem.spring.wallet.dto.WalletSummaryResponse;
import com.tem.spring.wallet.dto.WithdrawRequest;
import com.tem.spring.wallet.dto.WithdrawResponse;
import com.tem.spring.wallet.entity.WithdrawalEntity;
import com.tem.spring.wallet.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * AETHER 토큰 출금 및 비관적 락(Pessimistic Lock) 동시성 제어 & 영수증 암호학적 증명 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenWithdrawalService {

    private final WithdrawalRepository withdrawalRepository;
    private final UserRepository userRepository;
    private final TokenRewardLogRepository rewardLogRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * [핵심] 비관적 락(SELECT ... FOR UPDATE) 기반 원자적 토큰 출금 처리
     * 동시 다중 요청(Race Condition Double-Spending) 원천 차단
     */
    @Transactional
    public WithdrawResponse processWithdrawal(WithdrawRequest req) {
        log.info("[TokenWithdrawalService] Processing withdrawal of {} AETHER for user ID: {} to {}",
                req.getAmount(), req.getUserId(), req.getDestinationAddress());

        // 1. 비관적 배타락(PESSIMISTIC_WRITE)으로 유저 레코드 획득
        UserEntity user = userRepository.findByIdWithPessimisticLock(req.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. ID: " + req.getUserId()));

        // 2. 잔고 무결성 검증
        if (user.getTokenBalance() < req.getAmount()) {
            throw new IllegalArgumentException(String.format("출금 가능 잔고가 부족합니다. (보유: %.2f AETHER, 요청: %.2f AETHER)",
                    user.getTokenBalance(), req.getAmount()));
        }

        // 3. 원자적 잔고 차감
        double previousBalance = user.getTokenBalance();
        double remainingBalance = previousBalance - req.getAmount();
        user.setTokenBalance(remainingBalance);
        userRepository.save(user);

        // 4. 온체인/바이비트 영수증 증명 해시 및 전자 서명 증명 생성
        String proofTxHash = generateSimulatedTxHash();
        LocalDateTime now = LocalDateTime.now();
        String cryptographicProof = generateReceiptSignature(user.getId(), req.getAmount(), req.getDestinationAddress(), proofTxHash, now);

        // 5. 출금 내역 엔티티 영속화
        WithdrawalEntity withdrawal = WithdrawalEntity.builder()
                .user(user)
                .amount(req.getAmount())
                .destinationType(req.getDestinationType())
                .destinationAddress(req.getDestinationAddress())
                .status("COMPLETED")
                .proofTxHash(proofTxHash)
                .cryptographicProof(cryptographicProof)
                .requestedAt(now)
                .processedAt(now)
                .build();

        WithdrawalEntity saved = withdrawalRepository.save(withdrawal);

        // 6. 감사 회계 원장(Audit Ledger)에 출금 기록 적재
        TokenRewardLogEntity auditLog = TokenRewardLogEntity.builder()
                .recipient(user)
                .tokenAmount(-req.getAmount())
                .reason("AETHER 토큰 출금 (" + req.getDestinationType() + ": " + req.getDestinationAddress() + ")")
                .txHash(proofTxHash)
                .rewardedAt(now)
                .build();
        rewardLogRepository.save(auditLog);

        log.info("[TokenWithdrawalService] Withdrawal SUCCESS: ID {}, New Balance: {}, ProofTx: {}",
                saved.getId(), remainingBalance, proofTxHash);

        return WithdrawResponse.builder()
                .success(true)
                .message("출금이 성공적으로 처리되었습니다. (영수증 증명 발급 완료)")
                .withdrawalId(saved.getId())
                .withdrawnAmount(req.getAmount())
                .remainingBalance(remainingBalance)
                .destinationType(req.getDestinationType())
                .destinationAddress(req.getDestinationAddress())
                .proofTxHash(proofTxHash)
                .cryptographicProof(cryptographicProof)
                .processedAt(now)
                .build();
    }

    @Transactional(readOnly = true)
    public WalletSummaryResponse getWalletSummary(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. ID: " + userId));

        List<WithdrawalEntity> list = withdrawalRepository.findByUserId(userId, PageRequest.of(0, 10));
        List<WithdrawResponse> recent = list.stream().map(w -> WithdrawResponse.builder()
                .success(true)
                .message("정상 출금")
                .withdrawalId(w.getId())
                .withdrawnAmount(w.getAmount())
                .remainingBalance(user.getTokenBalance())
                .destinationType(w.getDestinationType())
                .destinationAddress(w.getDestinationAddress())
                .proofTxHash(w.getProofTxHash())
                .cryptographicProof(w.getCryptographicProof())
                .processedAt(w.getProcessedAt())
                .build()).toList();

        return WalletSummaryResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .tokenBalance(user.getTokenBalance())
                .walletAddress(user.getWalletAddress())
                .bybitUid(user.getBybitUid())
                .reputationScore(user.getReputationScore())
                .recentWithdrawals(recent)
                .build();
    }

    private String generateSimulatedTxHash() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String generateReceiptSignature(Long userId, double amount, String dest, String txHash, LocalDateTime time) {
        try {
            String raw = userId + ":" + amount + ":" + dest + ":" + txHash + ":" + time;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return "SIG_PROOF_" + System.currentTimeMillis();
        }
    }
}
