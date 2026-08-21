package com.tem.spring.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Spring AI + Ollama(로컬 LLM) + ChromaDB RAG 기반 정성적 분석 결과
 */
@Value
@Builder
public class QualitativeInsight {
    String symbol;
    String sentiment;              // BULLISH (호재), BEARISH (악재), NEUTRAL (중립)
    double sentimentScore;         // -1.0(극도 악재) ~ +1.0(극도 호재)
    double confidence;             // 0.0 ~ 1.0 LLM 판단 신뢰도
    String macroSummary;           // 거시경제 및 시장 주요 요약
    List<String> keyHeadlines;     // RAG로 검색된 주요 뉴스/공시 헤드라인
    String riskFactors;            // 주요 리스크 요인
}
