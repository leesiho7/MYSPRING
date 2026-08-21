package com.tem.spring.community.service;

import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.community.entity.TokenRewardLogEntity;
import com.tem.spring.community.repository.TokenRewardLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Web3 ERC-20 기여도 토큰(AETHER) 보상 지급 및 온체인 Tx 트랜잭션 시뮬레이션 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRewardService {

    private final TokenRewardLogRepository rewardLogRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public TokenRewardLogEntity issueTokenReward(UserEntity recipient, double amount, String reason) {
        String simulatedTxHash = generateSimulatedTxHash();
        log.info("[TokenRewardService] Rewarding {} AETHER to user {} (Tx: {}) - Reason: {}",
                amount, recipient.getUsername(), simulatedTxHash, reason);

        recipient.setReputationScore(recipient.getReputationScore() + (int)(amount * 2));
        userRepository.save(recipient);

        TokenRewardLogEntity logEntity = TokenRewardLogEntity.builder()
                .recipient(recipient)
                .tokenAmount(amount)
                .reason(reason)
                .txHash(simulatedTxHash)
                .rewardedAt(LocalDateTime.now())
                .build();

        return rewardLogRepository.save(logEntity);
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
}
