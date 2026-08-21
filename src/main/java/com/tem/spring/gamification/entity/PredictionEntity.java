package com.tem.spring.gamification.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 24H 캔들 방향성(Bull/Bear) 및 타겟 프라이스 스나이퍼 예측 엔티티 (MySQL)
 */
@Entity
@Table(name = "predictions", indexes = {
        @Index(name = "idx_pred_user", columnList = "user_id"),
        @Index(name = "idx_pred_symbol", columnList = "symbol"),
        @Index(name = "idx_pred_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 20)
    private String symbol; // BTCUSDT, NVDA 등

    @Column(nullable = false, length = 30)
    private String predictionType; // DIRECTION_24H, PRICE_SNIPER

    @Column(length = 10)
    private String predictedDirection; // BULL, BEAR

    private Double predictedPrice; // 스나이퍼 모드 시 목표가

    @Column(nullable = false)
    private Double entryPrice; // 예측 시점 기준 가격

    private Double settledPrice; // 정산 시점 실제 가격

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, WON, LOST, CANCELLED

    @Builder.Default
    private double rewardTokens = 0.0; // 적중 시 획득한 AETHER 토큰

    @Column(nullable = false)
    private LocalDateTime targetTime; // 정산 목표 시간

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
