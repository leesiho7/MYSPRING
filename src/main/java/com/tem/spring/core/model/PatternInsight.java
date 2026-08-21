package com.tem.spring.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * ChromaDB 벡터 기반 과거 차트 프랙탈/패턴 유사도 검색 결과
 */
@Value
@Builder
public class PatternInsight {
    String patternName;                // 예: "상승 깃발형 (Bullish Flag)", "이중 바닥 (Double Bottom)"
    String mostSimilarPeriod;          // 예: "2023-10-16 (비트코인 ETF 기대감 구간)"
    double similarityScore;            // 코사인 유사도 (0.0 ~ 1.0)
    double historicalWinRate;          // 과거 5일 후 상승 확률 (예: 0.80 -> 80%)
    double expectedReturn5Day;         // 과거 평균 5일 수익률 (예: 0.065 -> +6.5%)
    String patternSummary;             // 패턴 해설 요약
}
