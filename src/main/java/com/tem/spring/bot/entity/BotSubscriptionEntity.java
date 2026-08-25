package com.tem.spring.bot.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 24시간 가상 인스턴스 봇 호스팅 $7 USDT 먼슬리(30일) 구독 엔티티
 */
@Entity
@Table(name = "bot_subscriptions", indexes = {
        @Index(name = "idx_sub_user", columnList = "user_id"),
        @Index(name = "idx_sub_status", columnList = "status"),
        @Index(name = "idx_sub_end_date", columnList = "endDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String planName = "AETHER 24H BOT INSTANCE (MONTHLY)";

    @Column(nullable = false)
    @Builder.Default
    private double amountUsdt = 7.0; // 월 $7.0 USDT

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String paymentNetwork = "TRC20"; // TRC20, POLYGON, BSC, ARBITRUM, SOLANA

    @Column(length = 120)
    private String txHash; // 온체인 테더 결제 트랜잭션 해시

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, EXPIRED, PENDING, CANCELLED

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate; // 시작일 + 30일

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public boolean isCurrentlyActive() {
        return "ACTIVE".equalsIgnoreCase(status) && endDate != null && LocalDateTime.now().isBefore(endDate);
    }
}
