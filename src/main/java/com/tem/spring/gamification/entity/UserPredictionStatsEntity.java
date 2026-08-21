package com.tem.spring.gamification.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 유저별 예측 전적, 연속 적중 스트릭(Streak), 승률 및 티어(Oracle 등) 엔티티
 */
@Entity
@Table(name = "user_prediction_stats", indexes = {
        @Index(name = "idx_stat_user", columnList = "user_id", unique = true),
        @Index(name = "idx_stat_streak", columnList = "currentStreak"),
        @Index(name = "idx_stat_winrate", columnList = "winRatePct")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPredictionStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Builder.Default
    private int totalPredictions = 0;

    @Builder.Default
    private int wonPredictions = 0;

    @Builder.Default
    private int lostPredictions = 0;

    @Builder.Default
    private int currentStreak = 0; // 현재 연속 적중 횟수 (예: 5연승 🔥)

    @Builder.Default
    private int maxStreak = 0; // 역대 최장 연속 적중 횟수

    @Builder.Default
    private double winRatePct = 0.0; // 승률 %

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String tier = "NOVICE"; // ORACLE, GRAND_MASTER, MASTER, TRADER, NOVICE

    @Builder.Default
    private double totalEarnedTokens = 0.0; // 예측으로 획득한 총 AETHER 토큰

    public void updateStats(boolean won, double reward) {
        this.totalPredictions++;
        if (won) {
            this.wonPredictions++;
            this.currentStreak++;
            if (this.currentStreak > this.maxStreak) {
                this.maxStreak = this.currentStreak;
            }
            this.totalEarnedTokens += reward;
        } else {
            this.lostPredictions++;
            this.currentStreak = 0; // 연승 리셋
        }

        this.winRatePct = this.totalPredictions > 0 ?
                Math.round(((double) this.wonPredictions / this.totalPredictions) * 1000.0) / 10.0 : 0.0;

        // 티어 자동 승급 산정
        if (this.totalPredictions >= 10 && this.winRatePct >= 80.0 && this.maxStreak >= 5) {
            this.tier = "ORACLE"; // 💎 최고 권위자
        } else if (this.totalPredictions >= 8 && this.winRatePct >= 70.0) {
            this.tier = "GRAND_MASTER"; // 🥇 그랜드 마스터
        } else if (this.totalPredictions >= 5 && this.winRatePct >= 60.0) {
            this.tier = "MASTER"; // 🥈 마스터
        } else if (this.totalPredictions >= 3 && this.winRatePct >= 50.0) {
            this.tier = "TRADER"; // 🥉 트레이더
        } else {
            this.tier = "NOVICE"; // 🌱 루키
        }
    }
}
