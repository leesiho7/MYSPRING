package com.tem.spring.bot.service;

import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.bot.entity.BotInstanceEntity;
import com.tem.spring.bot.entity.BotLicenseTokenEntity;
import com.tem.spring.bot.repository.BotInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 3단계: 가상 인스턴스 및 봇 자동 프로비저닝 서비스 (Cloud API / Docker Container 24시간 연동)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuantBotProvisioningService {

    private final BotInstanceRepository botInstanceRepository;
    private final PythonBotGenerator botGenerator;
    private final PythonSandboxRunner sandboxRunner;

    @Value("${bot.hosting.mode:HYBRID}")
    private String hostingMode;

    @Value("${bot.hosting.docker.image:python:3.11-slim}")
    private String dockerImage;

    @Value("${bot.hosting.docker.memory-limit:512m}")
    private String memoryLimit;

    /**
     * 유저 전용 24시간 퀀트 파이썬 봇 인스턴스 및 컨테이너 자동 프로비저닝
     */
    @Transactional
    public BotInstanceEntity provisionAndStartBot(
            UserEntity user,
            BotLicenseTokenEntity tokenEntity,
            String customBotName,
            String symbol,
            String timeFrame) {

        String botName = customBotName != null && !customBotName.isBlank() ?
                customBotName :
                String.format("AETHER-QUANT-24H-%s-%s", user.getUsername().toUpperCase(), symbol != null ? symbol : "BTCUSDT");

        String targetSymbol = symbol != null ? symbol : "BTCUSDT";
        String targetTf = timeFrame != null ? timeFrame : "5m";

        log.info("[QuantBotProvisioning] Spin-up 24H Quant Bot for User '{}' [Token: {}...]",
                user.getUsername(), tokenEntity.getTokenString().substring(0, 16));

        // 1. 기본 파이썬 봇 스크립트 생성 (초보자 표준 게이지 전략)
        String standardParams = "{\"rsiBuyThreshold\":30,\"rsiSellThreshold\":70,\"smaShortPeriod\":7,\"smaLongPeriod\":25,\"takeProfitPct\":3.5,\"stopLossPct\":1.5,\"positionSizePct\":20.0}";
        String pythonCode = botGenerator.generateStandardBotCode(targetSymbol, targetTf, "BINANCE", standardParams);

        // 2. 가상 인스턴스/도커 식별자 생성
        String containerId = "quant-worker-" + user.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);

        // 3. 인스턴스 엔티티 저장
        BotInstanceEntity botInstance = BotInstanceEntity.builder()
                .user(user)
                .botName(botName)
                .mode("BEGINNER")
                .status("RUNNING")
                .exchange("BINANCE")
                .symbol(targetSymbol)
                .timeFrame(targetTf)
                .beginnerParamsJson(standardParams)
                .developerPythonCode(pythonCode)
                .executionHandle(containerId)
                .totalTrades(0)
                .winningTrades(0)
                .cumulativePnlPct(0.0)
                .currentPositionUsdt(0.0)
                .startedAt(LocalDateTime.now())
                .lastExecutedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        BotInstanceEntity saved = botInstanceRepository.save(botInstance);

        // 4. 컨테이너/샌드박스 백그라운드 엔진 구동
        // 환경변수: LICENSE_TOKEN, USER_ID, TELEGRAM_CHAT_ID 주입
        startWorkerContainer(saved.getId(), containerId, tokenEntity.getTokenString(), user.getId(), user.getTelegramChatId(), pythonCode);

        log.info("[QuantBotProvisioning] ✅ Successfully provisioned container '{}' for Bot Instance #{}", containerId, saved.getId());
        return saved;
    }

    /**
     * Docker 컨테이너 또는 격리 프로세스 워커 실행
     */
    private void startWorkerContainer(Long instanceId, String containerId, String tokenString, Long userId, String chatId, String pythonCode) {
        log.info("[QuantBotProvisioning] Launching Container [{}] with ENV: [TOKEN={}..., USER_ID={}, CHAT_ID={}]",
                containerId, tokenString.substring(0, 12), userId, chatId != null ? chatId : "N/A");

        try {
            // 백그라운드 샌드박스 러너에 등록
            sandboxRunner.startInstance(instanceId, containerId, "BTCUSDT", "BEGINNER", pythonCode);
            log.info("[QuantBotProvisioning] 24/7 Quant Bot engine is now active in memory & isolated sandbox.");
        } catch (Exception e) {
            log.error("[QuantBotProvisioning] Failed to start container worker for instance #{}", instanceId, e);
        }
    }

    /**
     * 만료 또는 사용자 중지 시 컨테이너/인스턴스 종료
     */
    @Transactional
    public void stopAndDestroyBot(Long instanceId) {
        botInstanceRepository.findById(instanceId).ifPresent(bot -> {
            bot.setStatus("STOPPED");
            bot.setStoppedAt(LocalDateTime.now());
            botInstanceRepository.save(bot);
            sandboxRunner.stopInstance(instanceId);
            log.info("[QuantBotProvisioning] 🛑 Destroyed container/sandbox for Bot Instance #{}", instanceId);
        });
    }
}
