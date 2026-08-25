package com.tem.spring.bot.controller;

import com.tem.spring.bot.dto.*;
import com.tem.spring.bot.service.BotInstanceService;
import com.tem.spring.bot.service.BotSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 24시간 가상 인스턴스 파이썬 자동매매 봇 ($7 USDT 먼슬리 호스팅) REST API 컨트롤러
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotHostingController {

    private final BotSubscriptionService subscriptionService;
    private final BotInstanceService instanceService;

    /**
     * 1. $7 USDT 먼슬리(30일) 봇 호스팅 구독 구매/활성화 API
     */
    @PostMapping("/subscription/purchase")
    public ResponseEntity<BotSubscriptionResponse> purchaseSubscription(@Valid @RequestBody PurchaseSubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionService.purchaseSubscription(request));
    }

    /**
     * 2. 유저의 현재 봇 호스팅 구독 상태 및 남은 기간 조회 API
     */
    @GetMapping("/subscription/{userId}")
    public ResponseEntity<BotSubscriptionResponse> getSubscriptionStatus(@PathVariable Long userId) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionStatus(userId));
    }

    /**
     * 3. 24시간 봇 가상 인스턴스 생성 또는 설정 갱신 API (초보자 게이지 또는 개발자 파이썬)
     */
    @PostMapping("/instance")
    public ResponseEntity<BotInstanceResponse> createOrUpdateBot(@Valid @RequestBody CreateBotInstanceRequest request) {
        return ResponseEntity.ok(instanceService.createOrUpdateBot(request));
    }

    /**
     * 4. 24시간 봇 가상 인스턴스 가동 시작 API ($7 활성 구독 필수)
     */
    @PostMapping("/instance/{instanceId}/start")
    public ResponseEntity<BotInstanceResponse> startBot(
            @PathVariable Long instanceId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(instanceService.startBot(instanceId, userId));
    }

    /**
     * 5. 봇 가상 인스턴스 중지 API
     */
    @PostMapping("/instance/{instanceId}/stop")
    public ResponseEntity<BotInstanceResponse> stopBot(
            @PathVariable Long instanceId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(instanceService.stopBot(instanceId, userId));
    }

    /**
     * 6. 봇 실시간 상태 및 누적 수익률 조회 API
     */
    @GetMapping("/instance/{instanceId}/status")
    public ResponseEntity<BotInstanceResponse> getBotStatus(
            @PathVariable Long instanceId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(instanceService.getBotStatus(instanceId, userId));
    }

    /**
     * 7. 유저가 생성한 전체 봇 인스턴스 목록 조회 API
     */
    @GetMapping("/instance/user/{userId}")
    public ResponseEntity<List<BotInstanceResponse>> getUserBots(@PathVariable Long userId) {
        return ResponseEntity.ok(instanceService.getUserBots(userId));
    }

    /**
     * 8. 봇 실시간 터미널 stdout 로그 조회 API
     */
    @GetMapping("/instance/{instanceId}/logs")
    public ResponseEntity<BotLogResponse> getBotLogs(
            @PathVariable Long instanceId,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(instanceService.getBotLogs(instanceId, limit));
    }

    /**
     * 9. [개발자 모드] 파이썬 코드 문법 및 보안 샌드박스 검증 & 백테스트 API
     */
    @PostMapping("/instance/test-code")
    public ResponseEntity<TestPythonCodeResponse> testPythonCode(@Valid @RequestBody TestPythonCodeRequest request) {
        return ResponseEntity.ok(instanceService.testPythonCode(request));
    }
}
