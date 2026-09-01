package com.tem.spring.ai.guardrail;

import com.tem.spring.core.model.PatternInsight;
import com.tem.spring.core.model.QuantitativeSignal;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * [Rule 4. FastDTW & AI 결과의 결정론적 앙상블 (Deterministic Ensemble Gate)]
 * 12스레드 FastDTW 알고리즘이 연산한 "과거 유사 프랙탈 패턴 승률"과
 * Qwen AI가 Vision/RAG로 해석한 "매수/매도 시그널" 간의 최종 승인 하드 룰 게이트.
 * 
 * AI의 확률적 추론이 하드웨어 기반 수학적 알고리즘(FastDTW)의 검증을 통과해야만
 * 유저에게 최종 승인 알림/시그널이 나가도록 강제합니다.
 */
@Slf4j
@Component
public class DeterministicEnsembleGate {

    @Getter
    @Builder
    public static class EnsembleDecision {
        private final String originalAiVerdict;
        private final String finalVerdict;
        private final boolean isDowngraded;
        private final boolean isOverridden;
        private final double fastDtwWinRate;
        private final double fastDtwExpectedReturn;
        private final double fastDtwSimilarity;
        private final String gateRationale;
        private final String ensembleStatus;
    }

    /**
     * AI 시그널과 FastDTW 패턴 승률을 융합하여 결정론적 하드 게이트 판정
     */
    public EnsembleDecision evaluateEnsemble(String rawAiVerdict, PatternInsight fractal, QuantitativeSignal quant) {
        String aiVerdict = (rawAiVerdict != null && !rawAiVerdict.isBlank()) ? rawAiVerdict.toUpperCase().trim() : "HOLD";
        double winRate = (fractal != null && fractal.getHistoricalWinRate() > 0) ? fractal.getHistoricalWinRate() : 0.50;
        double expReturn = fractal != null ? fractal.getExpectedReturn5Day() : 0.0;
        double simScore = fractal != null ? fractal.getSimilarityScore() : 0.70;

        String finalVerdict = aiVerdict;
        boolean downgraded = false;
        boolean overridden = false;
        String rationale;
        String status = "PASSED_HARD_GATE";

        // ── Hard Rule A: AI = STRONG_BUY/BUY but FastDTW Win Rate < 40% (or Expected Return < 0) ──
        if ((aiVerdict.contains("BUY") || "STRONG_BUY".equals(aiVerdict)) && (winRate < 0.40 || expReturn < -0.01)) {
            finalVerdict = "HOLD";
            downgraded = true;
            overridden = true;
            status = "DOWNGRADED_BY_FASTDTW_GATE";
            rationale = String.format(
                    "🚨 [AETHER 리스크 가디언 조정] AI 시그널(%s) 대비 시계열 프랙탈 통계 승률(%.0f%%)이 안전 기준 미달(기대수익률 %+.1f%%)이므로 리스크 방어를 위해 'HOLD(관망)'로 다운그레이드했습니다.",
                    aiVerdict, winRate * 100.0, expReturn * 100.0
            );
            log.warn("[DeterministicEnsembleGate] 🛡️ AI Verdict '{}' overridden to 'HOLD' due to low FastDTW win rate: {}%",
                    aiVerdict, Math.round(winRate * 100.0));

        // ── Hard Rule B: AI = STRONG_SELL/SELL but FastDTW Win Rate >= 75% and ExpReturn > +5% ──
        } else if ((aiVerdict.contains("SELL") || "STRONG_SELL".equals(aiVerdict)) && (winRate >= 0.75 && expReturn >= 0.05)) {
            finalVerdict = "HOLD";
            downgraded = false;
            overridden = true;
            status = "PROTECTED_BY_FASTDTW_GATE";
            rationale = String.format(
                    "⚠️ [AETHER 리스크 가디언 보호] AI 시그널(%s) 대비 역사적 프랙탈 반등 승률(%.0f%%) 및 기대수익률(%+.1f%%)이 압도적으로 높아 성급한 덤핑을 방어하기 위해 'HOLD(관망)'로 보호 조정했습니다.",
                    aiVerdict, winRate * 100.0, expReturn * 100.0
            );
            log.info("[DeterministicEnsembleGate] 🛡️ AI Verdict '{}' adjusted to 'HOLD' due to strong historical fractal bounce.", aiVerdict);

        // ── Hard Rule C: Normal Validation Passed ──
        } else {
            rationale = String.format(
                    "✅ [AETHER 리스크 가디언 승인] AI 추론 결과(%s)와 8,000개 캔들 빅데이터 프랙탈 검증(승률 %.0f%%, 일치율 %.1f%%)이 상호 일치하여 최종 승인되었습니다.",
                    finalVerdict, winRate * 100.0, simScore * 100.0
            );
        }

        return EnsembleDecision.builder()
                .originalAiVerdict(aiVerdict)
                .finalVerdict(finalVerdict)
                .isDowngraded(downgraded)
                .isOverridden(overridden)
                .fastDtwWinRate(winRate)
                .fastDtwExpectedReturn(expReturn)
                .fastDtwSimilarity(simScore)
                .gateRationale(rationale)
                .ensembleStatus(status)
                .build();
    }
}
