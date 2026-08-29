package com.tem.spring.gamification.controller;

import com.tem.spring.gamification.dto.HiveMindBattleResponse;
import com.tem.spring.gamification.dto.PredictionLeaderboardResponse;
import com.tem.spring.gamification.dto.PredictionResponse;
import com.tem.spring.gamification.dto.SubmitPredictionRequest;
import com.tem.spring.gamification.service.PredictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 24H 시장 예측, 연승 리더보드, AI vs Human 배틀 REST API 컨트롤러
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    /**
     * 1. 24H 방향성/스나이퍼 예측 제출 API (0.5 AETHER 지급)
     */
    @PostMapping("/submit")
    public ResponseEntity<PredictionResponse> submitPrediction(@Valid @RequestBody SubmitPredictionRequest request) {
        return ResponseEntity.ok(predictionService.submitPrediction(request));
    }

    /**
     * 2. 예측 결과 정산 API (적중 시 10.0 AETHER 지급 + 연승 갱신)
     */
    @PostMapping("/settle/{predictionId}")
    public ResponseEntity<PredictionResponse> settlePrediction(
            @PathVariable Long predictionId,
            @RequestParam(required = false) Double currentPrice) {
        return ResponseEntity.ok(predictionService.settlePrediction(predictionId, currentPrice));
    }

    /**
     * 3. 연속 적중(Streak) 및 승률 랭킹 리더보드 조회 API (Oracle 등 티어 배지)
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<PredictionLeaderboardResponse>> getLeaderboard(
            @RequestParam(defaultValue = "15") int limit) {
        return ResponseEntity.ok(predictionService.getLeaderboard(limit));
    }

    /**
     * 4. 특정 유저의 실시간 예측 통계 및 현재 연승(Streak) 조회 API
     */
    @GetMapping("/user-stats/{userId}")
    public ResponseEntity<PredictionLeaderboardResponse> getUserStats(@PathVariable Long userId) {
        return ResponseEntity.ok(predictionService.getUserStats(userId));
    }

    /**
     * 5. AI vs 인간 집단지성 배틀 현황 게이지 조회 API
     */
    @GetMapping("/battle")
    public ResponseEntity<HiveMindBattleResponse> getHiveMindBattle(
            @RequestParam(defaultValue = "BTCUSDT") String symbol) {
        return ResponseEntity.ok(predictionService.getHiveMindBattle(symbol));
    }
}
