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

    // News Headlines & Timestamps
    List<String> keyHeadlines;
    String chartSnapshotTime;
    String ragNewsCollectionTime;
    long latencyLagMinutes;
    boolean lagPenaltyActive;

    /**
     * LLM 프롬프트에 주입할 구조화된 XML 시맨틱 마켓 컨텍스트 생성 (Structural Grounding & Latency Lag Tracking)
     */
    public String toPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("<market_context symbol=\"").append(symbol != null ? symbol : "UNKNOWN").append("\">\n");

        sb.append("  <data_timestamps>\n");
        sb.append(String.format("    <chart_snapshot_kst>%s</chart_snapshot_kst>\n", chartSnapshotTime != null ? chartSnapshotTime : "Live Real-Time"));
        sb.append(String.format("    <rag_news_collection_kst>%s</rag_news_collection_kst>\n", ragNewsCollectionTime != null ? ragNewsCollectionTime : "Live Real-Time"));
        sb.append(String.format("    <latency_lag_minutes>%d</latency_lag_minutes>\n", latencyLagMinutes));
        sb.append(String.format("    <lag_penalty_applied>%s</lag_penalty_applied>\n", lagPenaltyActive ? "true (50% Weight Reduction on News)" : "false"));
        sb.append("  </data_timestamps>\n");

        sb.append("  <price_action>\n");
        sb.append(String.format("    <current_price>%.2f</current_price>\n", currentPrice));
        sb.append(String.format("    <hourly_open_price>%.2f</hourly_open_price>\n", hourlyStrikePrice));
        sb.append(String.format("    <hourly_delta_pct>%+.2f%%</hourly_delta_pct>\n", strikeDeltaPct));
        sb.append(String.format("    <strike_direction>%s</strike_direction>\n", strikeDirection));
        sb.append("  </price_action>\n");

        sb.append("  <technical_indicators engine=\"AETHER-Quant\">\n");
        sb.append(String.format("    <rsi period=\"14\" category=\"%s\">%.1f</rsi>\n", rsiCategory != null ? rsiCategory : "Neutral", rsi));
        sb.append(String.format("    <macd_status>%s</macd_status>\n", macdStatus != null ? macdStatus : "N/A"));
        sb.append(String.format("    <bollinger_band_status>%s</bollinger_band_status>\n", bollingerStatus != null ? bollingerStatus : "N/A"));
        sb.append(String.format("    <quant_score scale=\"-1.0_to_1.0\">%+.2f</quant_score>\n", quantScore));
        sb.append("  </technical_indicators>\n");

        sb.append("  <fractal_intelligence engine=\"AETHER-Fractal\">\n");
        sb.append(String.format("    <matched_pattern>%s</matched_pattern>\n", matchedFractalName != null ? matchedFractalName : "표준 프랙탈 반등 파동"));
        sb.append(String.format("    <similarity_score>%.1f%%</similarity_score>\n", similarityPct));
        sb.append(String.format("    <historical_5bar_win_rate>%.1f%%</historical_5bar_win_rate>\n", historicalWinRatePct));
        sb.append("  </fractal_intelligence>\n");

        sb.append("  <macro_and_news engine=\"BGE-M3 RAG\">\n");
        if (keyHeadlines != null && !keyHeadlines.isEmpty()) {
            for (String h : keyHeadlines) {
                sb.append("    <headline>").append(h.replace("<", "&lt;").replace(">", "&gt;")).append("</headline>\n");
            }
        } else {
            sb.append("    <headline>글로벌 외신 데이터 수집 중 · 온체인 유동성 안정세 유지</headline>\n");
        }
        sb.append("  </macro_and_news>\n");

        sb.append("</market_context>");
        return sb.toString();
    }
}
