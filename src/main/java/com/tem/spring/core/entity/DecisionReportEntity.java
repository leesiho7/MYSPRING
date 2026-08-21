package com.tem.spring.core.entity;

import com.tem.spring.core.model.ActionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 융합 의사결정 리포트 이력 저장을 위한 MySQL JPA 엔티티
 */
@Entity
@Table(name = "decision_reports", indexes = {
        @Index(name = "idx_report_symbol_date", columnList = "symbol, generatedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionType finalAction;

    @Column(nullable = false)
    private double totalScore;

    @Column(length = 500)
    private String divergenceRisk;

    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    private double quantScore;

    private double sentimentScore;

    private double patternWinRate;

    @Column(columnDefinition = "TEXT")
    private String agentReflection;

    @Column(nullable = false)
    private LocalDateTime generatedAt;
}
