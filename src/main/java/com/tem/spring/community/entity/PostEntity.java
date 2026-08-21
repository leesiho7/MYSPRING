package com.tem.spring.community.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 커뮤니티 금융/퀀트 분석 게시글 엔티티 (MySQL)
 * 종목 시그널 및 AI 팩트체크 검증 결과와 연동
 */
@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_post_symbol", columnList = "symbol"),
        @Index(name = "idx_post_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity author;

    @Column(nullable = false, length = 20)
    private String symbol; // 분석 종목 (예: BTCUSDT, NVDA)

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content; // 분석 내용

    @Column(length = 20)
    private String sentimentBias; // BULLISH, BEARISH, NEUTRAL

    private double targetPrice; // 목표가 (선택)

    @Column(columnDefinition = "TEXT")
    private String backtestSnapshot; // 첨부된 퀀트 백테스트 요약 (JSON 또는 문자열)

    @Builder.Default
    private boolean aiFactChecked = true; // AI 팩트체크 검증 여부

    @Column(columnDefinition = "TEXT")
    private String aiFactCheckSummary; // AI 팩트체크 요약 코멘트

    @Builder.Default
    private int likeCount = 0; // 좋아요 수

    @Builder.Default
    private double rewardTokenAmount = 0.0; // 획득한 ERC-20 보상 토큰 (AETHER)

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
