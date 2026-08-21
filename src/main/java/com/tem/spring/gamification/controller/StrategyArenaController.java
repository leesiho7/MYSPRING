package com.tem.spring.gamification.controller;

import com.tem.spring.gamification.dto.ArenaStrategyResponse;
import com.tem.spring.gamification.dto.SubmitArenaStrategyRequest;
import com.tem.spring.gamification.service.StrategyArenaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 소셜 퀀트 토너먼트 (Lego Strategy Arena) 및 전략 카피 REST API 컨트롤러
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/arena")
@RequiredArgsConstructor
public class StrategyArenaController {

    private final StrategyArenaService arenaService;

    /**
     * 1. 레고 블록 커스텀 전략 아레나 리그 출전 등록 API (10.0 AETHER 지급)
     */
    @PostMapping("/submit")
    public ResponseEntity<ArenaStrategyResponse> submitArenaStrategy(@Valid @RequestBody SubmitArenaStrategyRequest request) {
        return ResponseEntity.ok(arenaService.submitArenaStrategy(request));
    }

    /**
     * 2. 아레나 리그 실시간 랭킹 리더보드 조회 API (수익률, 손익비, 승률 랭킹)
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<ArenaStrategyResponse>> getArenaLeaderboard(
            @RequestParam(defaultValue = "SEASON_1") String season,
            @RequestParam(defaultValue = "15") int limit) {
        return ResponseEntity.ok(arenaService.getArenaLeaderboard(season, limit));
    }

    /**
     * 3. 1등/인기 전략 카피 트레이딩 복사 API (원작자에게 2.0 AETHER 수수료 지급)
     */
    @PostMapping("/{arenaId}/copy")
    public ResponseEntity<ArenaStrategyResponse> copyStrategy(
            @PathVariable Long arenaId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(arenaService.copyStrategy(arenaId, userId));
    }
}
