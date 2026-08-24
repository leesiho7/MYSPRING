package com.tem.spring.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 유저 AI 질문 및 RAG/LLM 생성 리서치 로그 저장 엔티티 (MySQL user_queries 테이블)
 */
@Entity
@Table(name = "user_queries", indexes = {
        @Index(name = "idx_query_symbol_date", columnList = "symbol, createdAt"),
        @Index(name = "idx_query_conv", columnList = "conversationId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(length = 64)
    private String userId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String llmResponse;

    @Column(length = 30)
    private String intentVerdict;

    private Integer entryQualityScore;

    @Column(columnDefinition = "TEXT")
    private String ragContext;

    private Long responseTimeMs;

    private Boolean isFallback;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
