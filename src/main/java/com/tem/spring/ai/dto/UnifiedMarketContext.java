package com.tem.spring.ai.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 정량적 기술 지표(ta4j) + FastDTW 8000 프랙탈 + 1시간 기준가 + BGE-M3 외신을 융합한 AI 전용 전처리 데이터 DTO
 */
@Value
@Builder
public class UnifiedMarketContext {
    String symbol;
    double currentPrice;
    double hourlyStrikePrice;
    double strikeDeltaPct;
    String strikeDirection; // "UP" or "DOWN"

    // ta4j Indicators
    double rsi;
    String rsiCategory; // "과매수(Overbought)", "과매도(Oversold)", "상승 모멘텀(Bullish)", "중립(Neutral)"
    boolean isGoldenCross;
    String macdStatus;
    String bollingerStatus;
    double quantScore;

    // FastDTW 8000 Pattern
    String matchedFractalName;
    double similarityPct;
    double historicalWinRatePct;

    // News Headlines
    List<String> keyHeadlines;

    /**
     * LLM 프롬프트에 주입할 최적화된 팩트 기반 요약 텍스트 시트 생성
     */
    public String toPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[AETHER QUANT PREPROCESSED CONTEXT]\n"));
        sb.append(String.format("1. 자산/가격: %s (현재가: $%.2f | 1시간봉 기준 시가: $%.2f | 기준가 대비: %+.2f%% [%s 우세])\n",
                symbol, currentPrice, hourlyStrikePrice, strikeDeltaPct, strikeDirection));
        sb.append(String.format("2. 기술적 지표(ta4j): RSI(14)=%.1f [%s] | MACD [%s] | 볼린저 [%s] | 퀀트 종합점수=%+.2f\n",
                rsi, rsiCategory, macdStatus, bollingerStatus, quantScore));
        sb.append(String.format("3. FastDTW 8,000 프랙탈 매칭: '%s' (과거 패턴 일치율: %.1f%% | 통계적 5봉 상승 승률: %.1f%%)\n",
                matchedFractalName != null ? matchedFractalName : "표준 프랙탈 반등 파동", similarityPct, historicalWinRatePct));
        sb.append("4. BGE-M3 RAG 실시간 외신 헤드라인:\n");
        if (keyHeadlines != null && !keyHeadlines.isEmpty()) {
            for (String h : keyHeadlines) {
                sb.append("   - ").append(h).append("\n");
            }
        } else {
            sb.append("   - (글로벌 외신 데이터 수집 중 · 온체인 유동성 안정세)\n");
        }
        return sb.toString();
    }
}
