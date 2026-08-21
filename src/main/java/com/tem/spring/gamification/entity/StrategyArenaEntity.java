package com.tem.spring.gamification.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 소셜 퀀트 토너먼트(Lego Strategy Arena) 출전 전략 엔티티
 */
@Entity
@Table(name = "strategy_arena", indexes = {
        @Index(name = "idx_arena_season", columnList = "season"),
        @Index(name = "idx_arena_return", columnList = "currentReturnPct"),
        @Index(name = "idx_arena_winrate", columnList = "winRatePct")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyArenaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity author;

    @Column(nullable = false, length = 100)
    private String strategyName; // 출전 전략명

    @Column(nullable = false, length = 20)
    private String symbol; // 대상 종목

    @Column(nullable = false, columnDefinition = "TEXT")
    private String strategyConfigJson; // 레고 블록 설정 JSON (RSI, SMA, Bollinger 등)

    @Builder.Default
    private double currentReturnPct = 0.0; // 실시간 수익률 %

    @Builder.Default
    private double winRatePct = 0.0; // 승률 %

    @Builder.Default
    private double profitFactor = 0.0; // 손익비 (PF)

    @Builder.Default
    private double maxDrawdownPct = 0.0; // MDD %

    @Builder.Default
    private int totalTrades = 0; // 체결 거래 수

    @Builder.Default
    private int copyCount = 0; // 다른 유저의 복사(Copy) 횟수

    @Builder.Default
    private int rankPosition = 1; // 실시간 랭킹 순위

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String season = "SEASON_1";

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
