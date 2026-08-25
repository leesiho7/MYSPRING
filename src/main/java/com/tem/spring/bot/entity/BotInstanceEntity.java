package com.tem.spring.bot.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 24시간 가상 파이썬 트레이딩 봇 인스턴스 엔티티 (초보자 모드 & 개발자 모드)
 */
@Entity
@Table(name = "bot_instances", indexes = {
        @Index(name = "idx_bot_user", columnList = "user_id"),
        @Index(name = "idx_bot_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 60)
    private String botName;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String mode = "BEGINNER"; // BEGINNER (게이지 파라미터), DEVELOPER (파이썬 코드 복사)

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "STOPPED"; // STOPPED, RUNNING, ERROR, EXPIRED

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String exchange = "BINANCE"; // BINANCE, BYBIT, UPBIT

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String symbol = "BTCUSDT";

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String timeFrame = "5m"; // 1m, 5m, 15m, 1h, 1d

    // 거래소 API 연동 키 (AES-256 암호화 저장)
    @Column(length = 255)
    private String apiKeyEncrypted;

    @Column(length = 255)
    private String apiSecretEncrypted;

    // 초보자 모드: JSON 직렬화된 파라미터 (RSI, SMA, 손절, 익절, 포지션비율)
    @Column(columnDefinition = "TEXT")
    private String beginnerParamsJson;

    // 개발자 모드: 고객이 직접 복사/붙여넣은 파이썬 자동매매 스크립트
    @Column(columnDefinition = "LONGTEXT")
    private String developerPythonCode;

    // 실행 중인 가상 프로세스/도커 컨테이너 식별자
    @Column(length = 100)
    private String executionHandle;

    // 24시간 실시간 트레이딩 통계
    @Column(nullable = false)
    @Builder.Default
    private int totalTrades = 0;

    @Column(nullable = false)
    @Builder.Default
    private int winningTrades = 0;

    @Column(nullable = false)
    @Builder.Default
    private double cumulativePnlPct = 0.0; // 누적 수익률 (%)

    @Column(nullable = false)
    @Builder.Default
    private double currentPositionUsdt = 0.0;

    private LocalDateTime startedAt;
    private LocalDateTime stoppedAt;
    private LocalDateTime lastExecutedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
