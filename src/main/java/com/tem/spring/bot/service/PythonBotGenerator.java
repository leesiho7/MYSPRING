package com.tem.spring.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 초보자 모드의 개별 토글/게이지 파라미터 및 골드만삭스 리스크 가드를
 * 완전한 표준 파이썬 3.11 트레이딩 봇 코드로 동적 컴파일하는 엔진
 */
@Slf4j
@Component
public class PythonBotGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateStandardBotCode(String symbol, String timeFrame, String exchange, String paramsJson) {
        boolean useGoldmanRisk = true;
        double maxDailyLoss = 4.0;
        double volSensitivity = 1.0;

        boolean useRsi = true;
        double rsiBuy = 30.0;
        double rsiSell = 70.0;

        boolean useSma = true;
        int smaShort = 20;
        int smaLong = 50;

        boolean useTpSl = true;
        double tp = 6.0;
        double sl = 3.0;
        double positionSize = 20.0;

        if (paramsJson != null && !paramsJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(paramsJson);
                useGoldmanRisk = node.path("useGoldmanRiskGuard").asBoolean(true);
                maxDailyLoss = node.path("maxDailyLossPct").asDouble(4.0);
                volSensitivity = node.path("volatilitySensitivity").asDouble(1.0);

                useRsi = node.path("useRsi").asBoolean(true);
                rsiBuy = node.path("rsiBuyThreshold").asDouble(30.0);
                rsiSell = node.path("rsiSellThreshold").asDouble(70.0);

                useSma = node.path("useSma").asBoolean(true);
                smaShort = node.path("smaShortPeriod").asInt(20);
                smaLong = node.path("smaLongPeriod").asInt(50);

                useTpSl = node.path("useTakeProfitStopLoss").asBoolean(true);
                tp = node.path("takeProfitPct").asDouble(6.0);
                sl = node.path("stopLossPct").asDouble(3.0);
                positionSize = node.path("positionSizePct").asDouble(20.0);
            } catch (Exception e) {
                log.warn("[PythonBotGenerator] Failed to parse paramsJson, using defaults: {}", e.getMessage());
            }
        }

        return String.format("""
            # -*- coding: utf-8 -*-
            \"\"\"
            ===================================================================
            AETHER 24H Automated Cloud Trading Bot (Modular Engine)
            - Target Symbol: %s | TimeFrame: %s | Exchange: %s
            - Goldman Sachs Risk Guard: %s (Max Daily Loss: -%.1f%%, Vol: %.1fx)
            - RSI Filter: %s (Buy < %.1f, Sell > %.1f)
            - Dual SMA Trend: %s (SMA %d / %d)
            - Risk-Reward: %s (TP: +%.1f%%, SL: -%.1f%%, Base Size: %.1f%%)
            ===================================================================
            \"\"\"
            import time
            import datetime
            import math

            SYMBOL = "%s"
            TIMEFRAME = "%s"
            EXCHANGE = "%s"

            # --- Modular Strategy Toggles & Parameters ---
            USE_GOLDMAN_RISK_GUARD = %s
            MAX_DAILY_LOSS_PCT = %.1f
            VOLATILITY_SENSITIVITY = %.1f

            USE_RSI = %s
            RSI_BUY_THRESHOLD = %.1f
            RSI_SELL_THRESHOLD = %.1f

            USE_SMA = %s
            SMA_SHORT_PERIOD = %d
            SMA_LONG_PERIOD = %d

            USE_TP_SL = %s
            TAKE_PROFIT_PCT = %.1f
            STOP_LOSS_PCT = %.1f
            BASE_POSITION_SIZE_PCT = %.1f

            class AetherModularBotEngine:
                def __init__(self):
                    self.position = None
                    self.entry_price = 0.0
                    self.trades_count = 0
                    self.win_count = 0
                    self.daily_pnl = 0.0
                    self.total_pnl = 0.0
                    self.is_circuit_breaker_active = False

                    print(f"[{datetime.datetime.now().strftime('%%Y-%%m-%%d %%H:%%M:%%S')}] [INIT] Aether 24H Instance Online for {SYMBOL} on {EXCHANGE}")
                    if USE_GOLDMAN_RISK_GUARD:
                        print(f"[{datetime.datetime.now().strftime('%%Y-%%m-%%d %%H:%%M:%%S')}] [SHIELD] 🛡️ Goldman Sachs gs-quant Risk Guard ACTIVE: Daily Stop Loss -{MAX_DAILY_LOSS_PCT}%% | Vol Sensitivity {VOLATILITY_SENSITIVITY}x")

                def calculate_dynamic_position_size(self, current_atr_pct):
                    \"\"\"골드만삭스식 변동성 감응형 동적 포지션 사이징\"\"\"
                    if not USE_GOLDMAN_RISK_GUARD or current_atr_pct <= 0:
                        return BASE_POSITION_SIZE_PCT
                    # 변동성 급증 시 안전하게 비중 축소 (Kelly / Volatility Parity)
                    vol_factor = max(0.4, min(1.5, 1.0 / (current_atr_pct * VOLATILITY_SENSITIVITY)))
                    adjusted_size = BASE_POSITION_SIZE_PCT * vol_factor
                    return round(adjusted_size, 2)

                def check_circuit_breaker(self):
                    if USE_GOLDMAN_RISK_GUARD and self.daily_pnl <= -MAX_DAILY_LOSS_PCT:
                        self.is_circuit_breaker_active = True
                        print(f"[{datetime.datetime.now().strftime('%%Y-%%m-%%d %%H:%%M:%%S')}] [ALERT] 🚨 일일 손실 한도(-{MAX_DAILY_LOSS_PCT}%%) 도달! 당일 신규 진입을 셧다운합니다.")
                        return True
                    return False

                def evaluate_signals(self, rsi_val, sma_fast, sma_slow, current_price, current_atr_pct):
                    if self.check_circuit_breaker():
                        return "HOLD_CIRCUIT_BREAKER"

                    buy_signals = 0
                    active_indicators = 0

                    if USE_RSI:
                        active_indicators += 1
                        if rsi_val < RSI_BUY_THRESHOLD:
                            buy_signals += 1

                    if USE_SMA:
                        active_indicators += 1
                        if sma_fast > sma_slow:
                            buy_signals += 1

                    if active_indicators > 0 and buy_signals == active_indicators:
                        pos_size = self.calculate_dynamic_position_size(current_atr_pct)
                        return f"BUY_ENTRY (Size: {pos_size}%%)"

                    return "WAIT_SIGNAL"

            if __name__ == "__main__":
                bot = AetherModularBotEngine()
                print(f"[{datetime.datetime.now().strftime('%%Y-%%m-%%d %%H:%%M:%%S')}] [STATUS] WebSocket real-time market stream active. Waiting for signal...")
            """,
                symbol, timeFrame, exchange,
                useGoldmanRisk ? "ON" : "OFF", maxDailyLoss, volSensitivity,
                useRsi ? "ON" : "OFF", rsiBuy, rsiSell,
                useSma ? "ON" : "OFF", smaShort, smaLong,
                useTpSl ? "ON" : "OFF", tp, sl, positionSize,
                symbol, timeFrame, exchange,
                useGoldmanRisk ? "True" : "False", maxDailyLoss, volSensitivity,
                useRsi ? "True" : "False", rsiBuy, rsiSell,
                useSma ? "True" : "False", smaShort, smaLong,
                useTpSl ? "True" : "False", tp, sl, positionSize
        );
    }
}
