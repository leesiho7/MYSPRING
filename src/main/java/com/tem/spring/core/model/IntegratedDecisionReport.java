package com.tem.spring.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 정량적 지표(ta4j)와 정성적 분석(LLM)을 융합한 최종 하이브리드 의사결정 리포트
 */
@Value
@Builder
public class IntegratedDecisionReport {
    String symbol;
    ActionType finalAction;           // STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
    double totalScore;                // 가중 융합 종합 점수 (-1.0 ~ +1.0)
    String divergenceRisk;            // 지표와 뉴스 간의 괴리 위험 경고
    String decisionReason;            // 종합 의사결정 사유 요약
    QuantitativeSignal quantSignal;   // ta4j 지표 세부사항
    QualitativeInsight qualInsight;   // LLM/RAG 뉴스 분석 세부사항
    PatternInsight patternInsight;    // Module 2: ChromaDB 차트 프랙탈/패턴 유사도 분석
    String agentReflection;           // Module 3: ChromaDB 의사결정 장기 기억 & 과거 복기
    PersonaAdvice personaAdvice;      // Module 4: ChromaDB 투자 대가(버핏/시몬스/달리오) 자문
    
    // Model Lineage & Dynamic Regime Tracking
    String marketRegime;              // HIGH_VOLATILITY, CONSOLIDATION, TRENDING, NORMAL
    String llmModelName;              // e.g. "qwen2.5:14b"
    String promptVersion;             // e.g. "v2.1"
    double quantWeight;               // 동적 할당된 정량 가중치
    double qualWeight;                // 동적 할당된 정성 가중치
    double patternWeight;             // 동적 할당된 패턴 가중치

    LocalDateTime generatedAt;
}
