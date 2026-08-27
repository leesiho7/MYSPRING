package com.tem.spring.bot.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 24시간 가상 인스턴스 퀀트 봇 SHA-256 라이선스 토큰 엔티티
 * Token = SHA-256(User_ID + Payment_TxHash + Timestamp + Secret_Key)
 */
@Entity
@Table(name = "bot_license_tokens", indexes = {
        @Index(name = "idx_lic_token", columnList = "tokenString", unique = true),
        @Index(name = "idx_lic_tx_hash", columnList = "paymentTxHash", unique = true),
        @Index(name = "idx_lic_user", columnList = "user_id"),
        @Index(name = "idx_lic_status", columnList = "isActive"),
        @Index(name = "idx_lic_expired_at", columnList = "expiredAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotLicenseTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SHA-256 암호화 라이선스 토큰 (64자 16진수)
    @Column(nullable = false, unique = true, length = 64)
    private String tokenString;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // 온체인 테더/코인 입금 트랜잭션 해시 (유니크 멱등성 보장)
    @Column(nullable = false, unique = true, length = 120)
    private String paymentTxHash;

    // 블록체인 네트워크 (TRC20, POLYGON, BSC, SOLANA, ARBITRUM)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String paymentNetwork = "TRC20";

    // 결제 입금 금액 ($7.0 USDT)
    @Column(nullable = false)
    @Builder.Default
    private double amountUsdt = 7.0;

    // 결제된 입금 지갑 주소
    @Column(length = 100)
    private String depositAddress;

    // 라이선스 활성화 여부
    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    // 공식 텔레그램 봇 1:1 딥링크 매핑 Chat ID
    @Column(length = 50)
    private String telegramChatId;

    // 프로비저닝된 봇 인스턴스 ID 매핑
    private Long assignedInstanceId;

    // Docker 컨테이너 또는 프로세스 식별자
    @Column(length = 120)
    private String containerId;

    @Column(nullable = false)
    private LocalDateTime startDate;

    // 만료일시 (startDate + 30일)
    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastUsedAt;

    /**
     * 라이선스 토큰이 현재 유효한지 확인
     */
    public boolean isValid() {
        return isActive && expiredAt != null && LocalDateTime.now().isBefore(expiredAt);
    }
}
