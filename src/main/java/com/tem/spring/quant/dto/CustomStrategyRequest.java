package com.tem.spring.quant.dto;

import com.tem.spring.core.model.TimeFrame;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 레고 블록 스타일 커스텀 퀀트 전략 요청 DTO
 * 사용자가 원하는 지표 조건 블록들을 ON/OFF 하고 파라미터를 조합합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomStrategyRequest {

    @Builder.Default
    private String symbol = "BTCUSDT";

    @Builder.Default
    private TimeFrame timeFrame = TimeFrame.D1;

    @Builder.Default
    private int limit = 200;

    @Builder.Default
    private String strategyName = "Custom Lego Strategy";

    // ──────────────────────────────────────────
    // 🧱 진입 조건 레고 블록 (Entry Rules)
    // ──────────────────────────────────────────
    @Builder.Default
    private boolean useRsiEntry = true; // [블록 1] RSI 과매도 상향 돌파
    @Builder.Default
    private int rsiPeriod = 14;
    @Builder.Default
    private double rsiBuyThreshold = 30.0;

    @Builder.Default
    private boolean useSmaCrossEntry = true; // [블록 2] 단기/장기 이평선 골든크로스 또는 정배열
    @Builder.Default
    private int smaShortPeriod = 20;
    @Builder.Default
    private int smaLongPeriod = 50;

    @Builder.Default
    private boolean useBollingerLowerEntry = false; // [블록 3] 볼린저 밴드 하단 터치 반등
    @Builder.Default
    private int bollingerPeriod = 20;

    @Builder.Default
    private String entryLogicOp = "AND"; // 진입 조건 결합 방식: "AND" 또는 "OR"

    // ──────────────────────────────────────────
    // 🧱 청산 조건 레고 블록 (Exit Rules)
    // ──────────────────────────────────────────
    @Builder.Default
    private boolean useRsiExit = true; // [블록 1] RSI 과매수 하향 돌파
    @Builder.Default
    private double rsiSellThreshold = 70.0;

    @Builder.Default
    private boolean useSmaCrossExit = true; // [블록 2] 데드크로스 또는 장기 이평선 이탈

    @Builder.Default
    private boolean useBollingerUpperExit = false; // [블록 3] 볼린저 밴드 상단 도달

    @Builder.Default
    private boolean useStopLoss = true; // [블록 4] 손절선 강제 청산
    @Builder.Default
    private double stopLossPct = 3.0; // 손절 % (예: 3.0 -> -3% 도달 시 칼손절)

    @Builder.Default
    private boolean useTakeProfit = false; // [블록 5] 익절선 목표 도달 청산
    @Builder.Default
    private double takeProfitPct = 6.0; // 익절 % (예: 6.0 -> +6% 도달 시 익절)

    @Builder.Default
    private String exitLogicOp = "OR"; // 청산 조건 결합 방식: "OR" 또는 "AND"
}
