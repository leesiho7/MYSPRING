package com.tem.spring.gamification.service;

import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.community.service.TokenRewardService;
import com.tem.spring.core.model.Candle;
import com.tem.spring.core.model.TimeFrame;
import com.tem.spring.gamification.dto.HiveMindBattleResponse;
import com.tem.spring.gamification.dto.PredictionLeaderboardResponse;
import com.tem.spring.gamification.dto.PredictionResponse;
import com.tem.spring.gamification.dto.SubmitPredictionRequest;
import com.tem.spring.gamification.entity.PredictionEntity;
import com.tem.spring.gamification.entity.UserPredictionStatsEntity;
import com.tem.spring.gamification.repository.PredictionRepository;
import com.tem.spring.gamification.repository.UserPredictionStatsRepository;
import com.tem.spring.ingestion.service.MarketDataIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 24H 방향성 예측, 스나이퍼 대결, 연승(Streak) 및 AI vs Human 배틀 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final UserPredictionStatsRepository statsRepository;
    private final UserRepository userRepository;
    private final MarketDataIngestionService ingestionService;
    private final TokenRewardService tokenRewardService;

    @Transactional
    public PredictionResponse submitPrediction(SubmitPredictionRequest req) {
        log.info("[PredictionService] User {} predicting {} for {}", req.getUserId(), req.getPredictedDirection(), req.getSymbol());

        UserEntity user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. ID: " + req.getUserId()));

        boolean is1H = "DIRECTION_1H".equalsIgnoreCase(req.getPredictionType());
        List<Candle> candles = ingestionService.getHistoricalData(req.getSymbol(), is1H ? TimeFrame.H1 : TimeFrame.D1, 5);
        double currentPrice = candles.isEmpty() ? 67840.0 : candles.get(candles.size() - 1).getClose();

        PredictionEntity prediction = PredictionEntity.builder()
                .user(user)
                .symbol(req.getSymbol().toUpperCase())
                .predictionType(req.getPredictionType() != null ? req.getPredictionType() : "DIRECTION_1H")
                .predictedDirection(req.getPredictedDirection() != null ? req.getPredictedDirection().toUpperCase() : "BULL")
                .predictedPrice(req.getPredictedPrice())
                .entryPrice(currentPrice)
                .status("PENDING")
                .rewardTokens(0.0)
                .targetTime(LocalDateTime.now().plusHours(is1H ? 1 : 24))
                .createdAt(LocalDateTime.now())
                .build();

        PredictionEntity saved = predictionRepository.save(prediction);

        // 참가 보상 0.5 AETHER
        tokenRewardService.issueTokenReward(user, 0.5, is1H ? "1H 시장 예측 챌린지 참가 보상" : "24H 시장 예측 챌린지 참가 보상");

        return mapToResponse(saved);
    }

    @Transactional
    public PredictionResponse settlePrediction(Long predictionId, Double customCurrentPrice) {
        PredictionEntity pred = predictionRepository.findById(predictionId)
                .orElseThrow(() -> new IllegalArgumentException("예측을 찾을 수 없습니다. ID: " + predictionId));

        if (!"PENDING".equals(pred.getStatus())) {
            return mapToResponse(pred);
        }

        double settlePrice = customCurrentPrice != null ? customCurrentPrice : pred.getEntryPrice() * 1.008; // 시뮬레이션: 1H 변동성 정산
        pred.setSettledPrice(settlePrice);

        boolean won = false;
        if ("DIRECTION_1H".equalsIgnoreCase(pred.getPredictionType()) || "DIRECTION_24H".equalsIgnoreCase(pred.getPredictionType())) {
            if ("BULL".equalsIgnoreCase(pred.getPredictedDirection()) || "UP".equalsIgnoreCase(pred.getPredictedDirection())) {
                won = settlePrice >= pred.getEntryPrice();
            } else {
                won = settlePrice < pred.getEntryPrice();
            }
        } else if ("PRICE_SNIPER".equalsIgnoreCase(pred.getPredictionType()) && pred.getPredictedPrice() != null) {
            double diffPct = Math.abs(settlePrice - pred.getPredictedPrice()) / pred.getPredictedPrice() * 100.0;
            won = diffPct <= 1.0; // 오차 1% 이내 시 적중
        }

        double reward = 0.0;
        if (won) {
            pred.setStatus("WON");
            reward = 10.0; // 적중 보상 10.0 AETHER
            pred.setRewardTokens(reward);
            tokenRewardService.issueTokenReward(pred.getUser(), reward, "24H 예측 챌린지 적중 보상 (ID: " + pred.getId() + ")");
        } else {
            pred.setStatus("LOST");
        }

        PredictionEntity saved = predictionRepository.save(pred);

        // 유저 전적 및 연승(Streak) 통계 갱신
        UserPredictionStatsEntity stats = statsRepository.findByUserId(pred.getUser().getId())
                .orElseGet(() -> UserPredictionStatsEntity.builder().user(pred.getUser()).build());
        stats.updateStats(won, reward);
        statsRepository.save(stats);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PredictionLeaderboardResponse> getLeaderboard(int limit) {
        List<UserPredictionStatsEntity> list = statsRepository.findTopLeaderboard(PageRequest.of(0, limit));
        List<PredictionLeaderboardResponse> result = new ArrayList<>();
        int rank = 1;
        for (UserPredictionStatsEntity s : list) {
            result.add(PredictionLeaderboardResponse.builder()
                    .rank(rank++)
                    .userId(s.getUser().getId())
                    .nickname(s.getUser().getNickname())
                    .walletAddress(s.getUser().getWalletAddress())
                    .tier(s.getTier())
                    .currentStreak(s.getCurrentStreak())
                    .maxStreak(s.getMaxStreak())
                    .winRatePct(s.getWinRatePct())
                    .totalPredictions(s.getTotalPredictions())
                    .wonPredictions(s.getWonPredictions())
                    .totalEarnedTokens(s.getTotalEarnedTokens())
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public HiveMindBattleResponse getHiveMindBattle(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase() : "BTCUSDT";
        long bullCount = predictionRepository.countBullVotesBySymbol(sym);
        long bearCount = predictionRepository.countBearVotesBySymbol(sym);
        int total = (int) (bullCount + bearCount);

        double bullPct = total > 0 ? ((double) bullCount / total) * 100.0 : 65.0;
        double bearPct = total > 0 ? ((double) bearCount / total) * 100.0 : 35.0;

        double aiScore = 0.55; // AI Bullish bias
        String aiDecision = "BULLISH";

        String winningSide = (aiDecision.equals("BULLISH") && bullPct >= 50.0) ? "CONSENSUS_AGREED" : "AI_VS_HUMAN_CONFLICT";
        String commentary = String.format("Chroma AI(%s, 확신도 %.2f) vs 인간 집단지성(Bull %.1f%% / Bear %.1f%%, 총 %d표) 대결 중!",
                aiDecision, aiScore, bullPct, bearPct, Math.max(total, 42));

        return HiveMindBattleResponse.builder()
                .symbol(sym)
                .aiConfidenceScore(aiScore)
                .aiDecision(aiDecision)
                .humanBullPercentage(Math.round(bullPct * 10.0) / 10.0)
                .humanBearPercentage(Math.round(bearPct * 10.0) / 10.0)
                .totalHumanVotes(Math.max(total, 42))
                .winningSide(winningSide)
                .battleCommentary(commentary)
                .build();
    }

    private PredictionResponse mapToResponse(PredictionEntity p) {
        return PredictionResponse.builder()
                .predictionId(p.getId())
                .userId(p.getUser().getId())
                .nickname(p.getUser().getNickname())
                .symbol(p.getSymbol())
                .predictionType(p.getPredictionType())
                .predictedDirection(p.getPredictedDirection())
                .predictedPrice(p.getPredictedPrice())
                .entryPrice(p.getEntryPrice())
                .settledPrice(p.getSettledPrice())
                .status(p.getStatus())
                .rewardTokens(p.getRewardTokens())
                .targetTime(p.getTargetTime())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
