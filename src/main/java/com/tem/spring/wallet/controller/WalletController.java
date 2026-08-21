package com.tem.spring.wallet.controller;

import com.tem.spring.wallet.dto.WalletSummaryResponse;
import com.tem.spring.wallet.dto.WithdrawRequest;
import com.tem.spring.wallet.dto.WithdrawResponse;
import com.tem.spring.wallet.service.TokenWithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AETHER 토큰 지갑 및 출금(비관적 락 동시성 제어) & 증명 영수증 REST API 컨트롤러
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final TokenWithdrawalService withdrawalService;

    /**
     * 1. AETHER 토큰 출금 실행 API (비관적 락 SELECT FOR UPDATE 기반 원자적 차감 & 증명 발급)
     */
    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.ok(withdrawalService.processWithdrawal(request));
    }

    /**
     * 2. 내 지갑 잔고, 연동 계정(Web3/Bybit), 출금 이력 및 증명 영수증 조회 API
     */
    @GetMapping("/summary/{userId}")
    public ResponseEntity<WalletSummaryResponse> getWalletSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(withdrawalService.getWalletSummary(userId));
    }
}
