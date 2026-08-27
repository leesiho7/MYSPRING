package com.tem.spring.bot.service;

import com.tem.spring.bot.entity.BotLicenseTokenEntity;
import com.tem.spring.bot.repository.BotLicenseTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 5단계: 라이선스 수명 주기(Lifecycle) 및 만료 자동 처리 스케줄러
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseLifecycleScheduler {

    private final BotLicenseTokenRepository licenseTokenRepository;
    private final QuantBotProvisioningService provisioningService;
    private final TelegramOfficialBotService telegramOfficialBotService;

    /**
     * 매 시간(정각) 만료된 라이선스 토큰 검사 및 봇 컨테이너 정지
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void scanAndExpireLicenses() {
        LocalDateTime now = LocalDateTime.now();
        List<BotLicenseTokenEntity> expiredList = licenseTokenRepository.findByIsActiveTrueAndExpiredAtBefore(now);

        if (expiredList.isEmpty()) {
            return;
        }

        log.info("[LicenseLifecycle] Found {} expired license tokens to deactivate.", expiredList.size());

        for (BotLicenseTokenEntity token : expiredList) {
            token.setActive(false);
            licenseTokenRepository.save(token);

            // 1. 가상 인스턴스 및 봇 컨테이너 중지
            if (token.getAssignedInstanceId() != null) {
                provisioningService.stopAndDestroyBot(token.getAssignedInstanceId());
            }

            // 2. 텔레그램 1:1 만료 안내 발송
            if (token.getTelegramChatId() != null && !token.getTelegramChatId().isBlank()) {
                telegramOfficialBotService.sendMessage(
                        token.getTelegramChatId(),
                        "⏳ *[AETHER 퀀트 봇 이용 만료 안내]*\n\n" +
                        "30일간의 가상 인스턴스 봇 호스팅 기간이 만료되어 인스턴스가 안전하게 중지되었습니다.\n" +
                        "재이용을 원하시면 대시보드에서 추가 입금(갱신)을 진행해 주세요. 🙏",
                        null
                );
            }

            log.info("[LicenseLifecycle] 🛑 Deactivated Token ID: {} for user: {}", token.getId(), token.getUser().getUsername());
        }
    }
}
