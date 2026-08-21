package com.tem.spring.quant.indicator;

import com.tem.spring.core.model.ActionType;
import com.tem.spring.core.model.QuantitativeSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;

import java.util.ArrayList;
import java.util.List;

/**
 * ta4j를 활용한 기술적 지표 계산 및 정량 스코어링 엔진
 */
@Slf4j
@Component
public class TechnicalIndicatorEngine {

    public QuantitativeSignal calculateSignals(BarSeries series) {
        if (series.getBarCount() < 20) {
            log.warn("[TechnicalIndicatorEngine] Not enough bars to calculate accurate indicators (bars={})", series.getBarCount());
            return fallbackSignal(series.getName());
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        int lastIndex = series.getEndIndex();
        double currentPrice = closePrice.getValue(lastIndex).doubleValue();

        // 1. RSI (14)
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        double rsiValue = rsiIndicator.getValue(lastIndex).doubleValue();

        // 2. 이평선 (SMA 20, SMA 50)
        SMAIndicator sma20 = new SMAIndicator(closePrice, 20);
        SMAIndicator sma50 = new SMAIndicator(closePrice, Math.min(50, series.getBarCount() - 1));
        double sma20Val = sma20.getValue(lastIndex).doubleValue();
        double sma50Val = sma50.getValue(lastIndex).doubleValue();

        double prevSma20 = sma20.getValue(lastIndex - 1).doubleValue();
        double prevSma50 = sma50.getValue(lastIndex - 1).doubleValue();
        boolean isGoldenCross = prevSma20 <= prevSma50 && sma20Val > sma50Val;
        boolean isDeadCross = prevSma20 >= prevSma50 && sma20Val < sma50Val;

        // 3. 볼린저 밴드 (20, 2 std)
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, 20);
        BollingerBandsMiddleIndicator bbMiddle = new BollingerBandsMiddleIndicator(sma20);
        BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMiddle, stdDev, DecimalNum.valueOf(2));
        BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMiddle, stdDev, DecimalNum.valueOf(2));

        double bbUpperVal = bbUpper.getValue(lastIndex).doubleValue();
        double bbMiddleVal = bbMiddle.getValue(lastIndex).doubleValue();
        double bbLowerVal = bbLower.getValue(lastIndex).doubleValue();

        // 4. 정량 점수 산출 (-1.0 ~ +1.0) & 시그널 요약
        List<String> signals = new ArrayList<>();
        double score = 0.0;

        // RSI 기반 점수
        String rsiStatus;
        if (rsiValue <= 30) {
            rsiStatus = "과매도(Oversold)";
            score += 0.4;
            signals.add("RSI(" + String.format("%.1f", rsiValue) + ") 과매도 구간 진입 - 반등 기대");
        } else if (rsiValue >= 70) {
            rsiStatus = "과매수(Overbought)";
            score -= 0.4;
            signals.add("RSI(" + String.format("%.1f", rsiValue) + ") 과매수 구간 진입 - 과열 주의");
        } else {
            rsiStatus = "중립(Neutral)";
        }

        // 골든/데드 크로스
        if (isGoldenCross) {
            score += 0.4;
            signals.add("20일/50일 이평선 골든크로스 발생 (강세 전환)");
        } else if (isDeadCross) {
            score -= 0.4;
            signals.add("20일/50일 이평선 데드크로스 발생 (약세 전환)");
        }

        // 볼린저 밴드 위치
        if (currentPrice <= bbLowerVal) {
            score += 0.2;
            signals.add("볼린저 밴드 하단 터치 (기술적 지지선)");
        } else if (currentPrice >= bbUpperVal) {
            score -= 0.2;
            signals.add("볼린저 밴드 상단 돌파 (단기 저항선)");
        }

        // 점수 클램핑 (-1.0 ~ 1.0)
        score = Math.max(-1.0, Math.min(1.0, score));

        // 추천 액션
        ActionType action;
        if (score >= 0.5) action = ActionType.STRONG_BUY;
        else if (score >= 0.2) action = ActionType.BUY;
        else if (score <= -0.5) action = ActionType.STRONG_SELL;
        else if (score <= -0.2) action = ActionType.SELL;
        else action = ActionType.HOLD;

        return QuantitativeSignal.builder()
                .symbol(series.getName())
                .currentPrice(currentPrice)
                .rsi(rsiValue)
                .rsiStatus(rsiStatus)
                .goldenCross(isGoldenCross)
                .deadCross(isDeadCross)
                .sma20(sma20Val)
                .sma50(sma50Val)
                .bollingerUpper(bbUpperVal)
                .bollingerMiddle(bbMiddleVal)
                .bollingerLower(bbLowerVal)
                .suggestedAction(action)
                .quantScore(score)
                .signalsSummary(signals)
                .build();
    }

    private QuantitativeSignal fallbackSignal(String symbol) {
        return QuantitativeSignal.builder()
                .symbol(symbol)
                .currentPrice(0.0)
                .rsi(50.0)
                .rsiStatus("데이터 부족")
                .suggestedAction(ActionType.HOLD)
                .quantScore(0.0)
                .signalsSummary(List.of("데이터 부족으로 인한 지표 계산 불가"))
                .build();
    }
}
