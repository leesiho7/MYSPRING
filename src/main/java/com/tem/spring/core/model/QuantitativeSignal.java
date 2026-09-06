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
    Double vwap;                  // 기관 거래량 가중 평균 가격 (VWAP)
    Double atr;                   // 최근 14봉 변동성 (ATR)
    Double atrTrailingStop;       // ATR 1.5배수 가변 동적 트레일링 스탑 가격
    Double orderbookImbalance;    // 오더북 매수/매도 불균형 비율 (-1.0 ~ 1.0)
    Double fundingRate;           // 선물 펀딩비율 (8h %)
    List<String> signalsSummary;  // 주요 감지 시그널 목록
}
