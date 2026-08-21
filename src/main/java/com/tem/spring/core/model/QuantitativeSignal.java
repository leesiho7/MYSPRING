package com.tem.spring.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * ta4j 엔진에서 계산된 정량적 기술 지표 결과
 */
@Value
@Builder
public class QuantitativeSignal {
    String symbol;
    double currentPrice;
    double rsi;
    String rsiStatus;             // 과매도, 과매수, 중립
    boolean goldenCross;          // 단기/장기 이평선 골든크로스 여부
    boolean deadCross;            // 데드크로스 여부
    double sma20;
    double sma50;
    double bollingerUpper;
    double bollingerMiddle;
    double bollingerLower;
    ActionType suggestedAction;   // 지표 기반 추천
    double quantScore;            // -1.0 (강한 매도) ~ +1.0 (강한 매수)
    List<String> signalsSummary;  // 주요 감지 시그널 목록
}
