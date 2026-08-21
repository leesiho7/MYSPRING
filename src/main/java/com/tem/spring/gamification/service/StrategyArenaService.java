package com.tem.spring.gamification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.community.service.TokenRewardService;
import com.tem.spring.core.model.BacktestResult;
import com.tem.spring.core.model.Candle;
import com.tem.spring.core.model.TimeFrame;
import com.tem.spring.gamification.dto.ArenaStrategyResponse;
import com.tem.spring.gamification.dto.SubmitArenaStrategyRequest;
import com.tem.spring.gamification.entity.StrategyArenaEntity;
import com.tem.spring.gamification.repository.StrategyArenaRepository;
import com.tem.spring.ingestion.service.MarketDataIngestionService;
import com.tem.spring.quant.adapter.BarSeriesMapper;
import com.tem.spring.quant.strategy.BacktestingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 소셜 퀀트 토너먼트 (Lego Strategy Arena) 및 전략 카피 트레이딩 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyArenaService {

    private final StrategyArenaRepository arenaRepository;
    private final UserRepository userRepository;
    private final MarketDataIngestionService ingestionService;
    private final BarSeriesMapper barSeriesMapper;
    private final BacktestingEngine backtestingEngine;
    private final TokenRewardService tokenRewardService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ArenaStrategyResponse submitArenaStrategy(SubmitArenaStrategyRequest req) {
        log.info("[StrategyArenaService] User {} submitting strategy '{}' to Arena", req.getAuthorId(), req.getStrategyName());

        UserEntity author = userRepository.findById(req.getAuthorId())
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. ID: " + req.getAuthorId()));

        List<Candle> candles = ingestionService.getHistoricalData(req.getSymbol(), TimeFrame.D1, 200);
        BarSeries series = barSeriesMapper.toBarSeries(req.getSymbol(), candles);

        // 레고 블록 전략 백테스트 실시간 검증 실행
        BacktestResult result = backtestingEngine.runCustomStrategy(series, req.getStrategyConfig());

        String configJson = "";
        try {
            configJson = objectMapper.writeValueAsString(req.getStrategyConfig());
        } catch (Exception e) {
            configJson = "{}";
        }

        StrategyArenaEntity arena = StrategyArenaEntity.builder()
                .author(author)
                .strategyName(req.getStrategyName())
                .symbol(req.getSymbol().toUpperCase())
                .strategyConfigJson(configJson)
                .currentReturnPct(result.getGrossReturnPercentage())
                .winRatePct(result.getWinRatePercentage())
                .profitFactor(result.getProfitFactor())
                .maxDrawdownPct(result.getMaxDrawdownPercentage())
                .totalTrades(result.getTotalTrades())
                .copyCount(0)
                .rankPosition(1)
                .season("SEASON_1")
                .createdAt(LocalDateTime.now())
                .build();

        StrategyArenaEntity saved = arenaRepository.save(arena);

        // 아레나 리그 출전 보상 10.0 AETHER
        tokenRewardService.issueTokenReward(author, 10.0, "퀀트 아레나 리그 전략 등록 보상: " + req.getStrategyName());

        return mapToResponse(saved, 1);
    }

    @Transactional
    public ArenaStrategyResponse copyStrategy(Long arenaId, Long userId) {
        StrategyArenaEntity arena = arenaRepository.findById(arenaId)
                .orElseThrow(() -> new IllegalArgumentException("전략을 찾을 수 없습니다. ID: " + arenaId));

        arena.setCopyCount(arena.getCopyCount() + 1);
        StrategyArenaEntity saved = arenaRepository.save(arena);

        // 전략 원작자에게 카피 수수료 보상 2.0 AETHER 지급!
        tokenRewardService.issueTokenReward(arena.getAuthor(), 2.0, "소셜 퀀트 전략 카피 수수료 획득: " + arena.getStrategyName());

        return mapToResponse(saved, 1);
    }

    @Transactional(readOnly = true)
    public List<ArenaStrategyResponse> getArenaLeaderboard(String season, int limit) {
        String s = season != null ? season : "SEASON_1";
        List<StrategyArenaEntity> list = arenaRepository.findTopArenaLeaderboard(s, PageRequest.of(0, limit));

        List<ArenaStrategyResponse> result = new ArrayList<>();
        int rank = 1;
        for (StrategyArenaEntity a : list) {
            result.add(mapToResponse(a, rank++));
        }
        return result;
    }

    private ArenaStrategyResponse mapToResponse(StrategyArenaEntity a, int rank) {
        return ArenaStrategyResponse.builder()
                .arenaId(a.getId())
                .rank(rank)
                .authorId(a.getAuthor().getId())
                .authorNickname(a.getAuthor().getNickname())
                .authorWallet(a.getAuthor().getWalletAddress())
                .strategyName(a.getStrategyName())
                .symbol(a.getSymbol())
                .currentReturnPct(a.getCurrentReturnPct())
                .winRatePct(a.getWinRatePct())
                .profitFactor(a.getProfitFactor())
                .maxDrawdownPct(a.getMaxDrawdownPct())
                .totalTrades(a.getTotalTrades())
                .copyCount(a.getCopyCount())
                .season(a.getSeason())
                .strategyConfigJson(a.getStrategyConfigJson())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
