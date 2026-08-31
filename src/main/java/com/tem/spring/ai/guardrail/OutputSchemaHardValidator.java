package com.tem.spring.ai.guardrail;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * [Rule 3. Output Schema Hard Assertion (AI 응답 2차 하드 검증)]
 * Qwen 또는 Vision AI가 분석 결과로 JSON/수치를 리턴했을 때,
 * 손절가(Stop Loss), 목표가(Target Price), 매수/매도 시그널 수치가
 * 수학적으로 말도 안 되는 값(예: 목표가 $0, 음수, 현재가 대비 100배 괴리 등)인 경우
 * 이를 100% 기계적으로 검증하여 유효한 퀀트 범위 내로 교정하거나 안전한 기본값으로 복구합니다.
 */
@Slf4j
@Component
public class OutputSchemaHardValidator {

    @Getter
    @Builder
    public static class ValidatedPriceTargets {
        private final double currentPrice;
        private final double supportPrice;
        private final double resistancePrice;
        private final double targetPrice;
        private final double stopLossPrice;
        private final boolean wasAdjusted;
        private final String adjustmentReason;
    }

    /**
     * 가격 타겟 수치(지지선, 저항선, 목표가, 손절가)에 대한 수학적 결정론적 하드 검증
     */
    public ValidatedPriceTargets validateAndEnforceTargets(String symbol, double currentPrice,
                                                          Double supportPrice, Double resistancePrice,
                                                          Double targetPrice, Double stopLossPrice,
                                                          String intentVerdict) {
        double safeCurrent = currentPrice > 0 ? currentPrice : 100.0;
        double sPrice = (supportPrice != null && supportPrice > 0) ? supportPrice : safeCurrent * 0.98;
        double rPrice = (resistancePrice != null && resistancePrice > 0) ? resistancePrice : safeCurrent * 1.035;
        double tPrice = (targetPrice != null && targetPrice > 0) ? targetPrice : rPrice;
        double slPrice = (stopLossPrice != null && stopLossPrice > 0) ? stopLossPrice : sPrice * 0.98;

        boolean adjusted = false;
        StringBuilder reason = new StringBuilder();

        // 1. 목표가 검증 ($0 또는 터무니없는 값 차단)
        if (tPrice <= 0 || tPrice > safeCurrent * 10.0 || tPrice < safeCurrent * 0.1) {
            log.warn("[OutputSchemaValidator] ⚠️ Malformed target price detected (${}) for {}. Resetting to +3.5% from current.", tPrice, symbol);
            tPrice = Math.round(safeCurrent * 1.035 * 100.0) / 100.0;
            adjusted = true;
            reason.append("Target price out of sane bounds. ");
        }

        // 2. 손절가 검증 ($0 또는 음수 또는 현재가보다 높은 비정상 차단)
        if (slPrice <= 0 || slPrice >= safeCurrent || slPrice < safeCurrent * 0.5) {
            log.warn("[OutputSchemaValidator] ⚠️ Malformed stop-loss detected (${}) for {}. Resetting to -5.2% from current.", slPrice, symbol);
            slPrice = Math.round(safeCurrent * 0.948 * 100.0) / 100.0;
            adjusted = true;
            reason.append("Stop-loss price out of sane bounds. ");
        }

        // 3. 지지선/저항선 논리 일관성 검증 (Support < Resistance)
        if (sPrice >= rPrice) {
            log.warn("[OutputSchemaValidator] ⚠️ Support (${}) >= Resistance (${}) anomaly for {}. Correcting bounds.", sPrice, rPrice, symbol);
            sPrice = Math.round(safeCurrent * 0.98 * 100.0) / 100.0;
            rPrice = Math.round(safeCurrent * 1.035 * 100.0) / 100.0;
            adjusted = true;
            reason.append("Support/Resistance inverted. ");
        }

        // 4. BUY 포지션 시그널 정합성 검증 (Target > Current && StopLoss < Current)
        String verdict = intentVerdict != null ? intentVerdict.toUpperCase() : "BUY";
        if (verdict.contains("BUY")) {
            if (tPrice <= safeCurrent) {
                tPrice = Math.round(safeCurrent * 1.05 * 100.0) / 100.0;
                adjusted = true;
                reason.append("BUY target price must exceed current price. ");
            }
            if (slPrice >= safeCurrent) {
                slPrice = Math.round(safeCurrent * 0.95 * 100.0) / 100.0;
                adjusted = true;
                reason.append("BUY stop-loss must be below current price. ");
            }
        }

        return ValidatedPriceTargets.builder()
                .currentPrice(safeCurrent)
                .supportPrice(sPrice)
                .resistancePrice(rPrice)
                .targetPrice(tPrice)
                .stopLossPrice(slPrice)
                .wasAdjusted(adjusted)
                .adjustmentReason(adjusted ? reason.toString().trim() : "All targets passed mathematical schema assertions.")
                .build();
    }
}
