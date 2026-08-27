package com.tem.spring.bot.controller;

import com.tem.spring.bot.dto.TelegramUpdateDto;
import com.tem.spring.bot.service.TelegramOfficialBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 4단계: 텔레그램 공식 봇 웹훅 및 1:1 딥링크 연동 REST API 컨트롤러
 */
@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramOfficialBotService telegramOfficialBotService;

    /**
     * 1. 텔레그램 공식 서버로부터 수신되는 Webhook 업데이트 엔드포인트
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> receiveTelegramWebhook(@RequestBody TelegramUpdateDto update) {
        try {
            telegramOfficialBotService.processTelegramUpdate(update);
        } catch (Exception e) {
            log.error("[TelegramWebhookController] Error processing telegram update", e);
        }
        return ResponseEntity.ok("OK");
    }

    /**
     * 2. 공식 봇 정보 및 딥링크 생성 가이드 조회 API (프론트엔드 연동용)
     */
    @GetMapping("/bot-info")
    public ResponseEntity<Map<String, String>> getBotInfo() {
        return ResponseEntity.ok(Map.of(
                "botUsername", telegramOfficialBotService.getBotUsername(),
                "deepLinkTemplate", "https://t.me/" + telegramOfficialBotService.getBotUsername() + "?start={SHA256_LICENSE_TOKEN}",
                "description", "AETHER 24H 퀀트 인텔리전스 공식 1:1 봇"
        ));
    }

    /**
     * 3. [개발/테스트용] 텔레그램 /start 토큰 연동 수동 시뮬레이션 API
     */
    @PostMapping("/simulate-start")
    public ResponseEntity<Map<String, Object>> simulateTelegramStart(
            @RequestParam String token,
            @RequestParam String chatId,
            @RequestParam(defaultValue = "TraderUser") String username) {

        boolean linked = telegramOfficialBotService.linkTokenAndChatId(token, chatId, username);
        return ResponseEntity.ok(Map.of(
                "success", linked,
                "token", token,
                "chatId", chatId,
                "message", linked ? "성공적으로 1:1 텔레그램 봇이 연동되었습니다!" : "유효하지 않거나 만료된 토큰입니다."
        ));
    }
}
