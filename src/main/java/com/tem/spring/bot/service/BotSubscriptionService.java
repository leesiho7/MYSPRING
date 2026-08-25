package com.tem.spring.bot.service;

import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.bot.dto.BotSubscriptionResponse;
import com.tem.spring.bot.dto.PurchaseSubscriptionRequest;
import com.tem.spring.bot.entity.BotSubscriptionEntity;
import com.tem.spring.bot.repository.BotSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * $7 USDT 먼슬리(30일) 봇 호스팅 구독 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotSubscriptionService {

    private final BotSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    /**
     * 1. $7 USDT 먼슬리 구독 구매 및 30일 이용권 활성화
     */
    @Transactional
    public BotSubscriptionResponse purchaseSubscription(PurchaseSubscriptionRequest req) {
        log.info("[BotSubscriptionService] Purchasing $7 monthly hosting plan for user ID: {}", req.getUserId());

        UserEntity user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. ID: " + req.getUserId()));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusDays(30);

        // 기존에 활성 구독이 있는 경우 기존 만료일에 +30일 연장
        List<BotSubscriptionEntity> existingActive = subscriptionRepository.findActiveSubscriptionsByUserId(user.getId(), now);
        if (!existingActive.isEmpty()) {
            BotSubscriptionEntity latest = existingActive.get(0);
            endDate = latest.getEndDate().plusDays(30);
            latest.setStatus("EXTENDED");
            subscriptionRepository.save(latest);
        }

        String txHash = req.getTxHash() != null && !req.getTxHash().isBlank() ?
                req.getTxHash() :
                "0x" + UUID.randomUUID().toString().replace("-", "") + "USDT7";

        BotSubscriptionEntity subscription = BotSubscriptionEntity.builder()
                .user(user)
                .planName("AETHER 24H BOT INSTANCE (MONTHLY 30-DAY)")
                .amountUsdt(req.getAmountUsdt() > 0 ? req.getAmountUsdt() : 7.0)
                .paymentNetwork(req.getPaymentNetwork() != null ? req.getPaymentNetwork() : "TRC20")
                .txHash(txHash)
                .status("ACTIVE")
                .startDate(now)
                .endDate(endDate)
                .createdAt(now)
                .build();

        BotSubscriptionEntity saved = subscriptionRepository.save(subscription);
        log.info("[BotSubscriptionService] ✅ Activated 30-day Bot Hosting for user {}. Valid until: {}", user.getUsername(), endDate);

        long remainingDays = Math.max(0, Duration.between(now, endDate).toDays());

        return BotSubscriptionResponse.builder()
                .success(true)
                .message("24시간 가상 인스턴스 30일 구독이 성공적으로 활성화되었습니다! ($7 USDT 결제 완료)")
                .subscriptionId(saved.getId())
                .userId(user.getId())
                .planName(saved.getPlanName())
                .amountUsdt(saved.getAmountUsdt())
                .paymentNetwork(saved.getPaymentNetwork())
                .txHash(saved.getTxHash())
                .status(saved.getStatus())
                .active(true)
                .remainingDays(remainingDays)
                .startDate(saved.getStartDate())
                .endDate(saved.getEndDate())
                .build();
    }

    /**
     * 2. 특정 유저의 현재 구독 상태 및 남은 기간 조회
     */
    @Transactional(readOnly = true)
    public BotSubscriptionResponse getSubscriptionStatus(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<BotSubscriptionEntity> activeSubs = subscriptionRepository.findActiveSubscriptionsByUserId(userId, now);

        if (activeSubs.isEmpty()) {
            return BotSubscriptionResponse.builder()
                    .success(false)
                    .message("활성화된 봇 호스팅 구독이 없습니다. (월 $7 USDT)")
                    .userId(userId)
                    .status("INACTIVE")
                    .active(false)
                    .remainingDays(0)
                    .build();
        }

        BotSubscriptionEntity active = activeSubs.get(0);
        long remainingDays = Math.max(0, Duration.between(now, active.getEndDate()).toDays());

        return BotSubscriptionResponse.builder()
                .success(true)
                .message("24시간 가상 인스턴스 호스팅 서비스가 정상 이용 중입니다.")
                .subscriptionId(active.getId())
                .userId(userId)
                .planName(active.getPlanName())
                .amountUsdt(active.getAmountUsdt())
                .paymentNetwork(active.getPaymentNetwork())
                .txHash(active.getTxHash())
                .status("ACTIVE")
                .active(true)
                .remainingDays(remainingDays)
                .startDate(active.getStartDate())
                .endDate(active.getEndDate())
                .build();
    }

    /**
     * 3. 유저의 활성 구독 여부 불리언 체크
     */
    public boolean hasActiveSubscription(Long userId) {
        return !subscriptionRepository.findActiveSubscriptionsByUserId(userId, LocalDateTime.now()).isEmpty();
    }

    /**
     * 4. 매 시간마다 만료된 구독 자동 상태 갱신 (배치 스케줄러)
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void checkAndExpireSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<BotSubscriptionEntity> expired = subscriptionRepository.findByStatusAndEndDateBefore("ACTIVE", now);
        for (BotSubscriptionEntity sub : expired) {
            sub.setStatus("EXPIRED");
            subscriptionRepository.save(sub);
            log.info("[BotSubscriptionService] Expired subscription ID: {} for user ID: {}", sub.getId(), sub.getUser().getId());
        }
    }
}
