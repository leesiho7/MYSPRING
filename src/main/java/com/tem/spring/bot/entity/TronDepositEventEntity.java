package com.tem.spring.bot.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TRC-20 USDT 입금 감지 완료 후 기록하는 입금 이벤트 엔티티
 * - 19 컨펌 확인 즉시 저장 → 라이선스 자동 발급 트리거
 * - TxID 유니크 제약으로 이중 처리 완전 차단
 */
@Entity
@Table(name = "tron_deposit_events", indexes = {
        @Index(name = "idx_tron_evt_txid",   columnList = "txId", unique = true),
        @Index(name = "idx_tron_evt_user",   columnList = "user_id"),
        @Index(name = "idx_tron_evt_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TronDepositEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // TronGrid 트랜잭션 ID (SHA-256 64자)
    @Column(nullable = false, unique = true, length = 70)
    private String txId;

    // USDT TRC-20 컨트랙트 주소 (Shasta: TG3XXyExBkPp9nzdajDZsozEu4B5U2U2AZ)
    @Column(nullable = false, length = 42)
    private String contractAddress;

    // 송신 지갑 주소 (유저의 거래소 출금 주소)
    @Column(length = 42)
    private String fromAddress;

    // 수신 지갑 주소 (유저 고유 입금 주소)
    @Column(nullable = false, length = 42)
    private String toAddress;

    // USDT 수신 금액 (소수점 6자리, TRC-20 decimals=6)
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsdt;

    // 블록 번호
    private Long blockNumber;

    // 트랜잭션 컨펌 수 (19 이상 시 처리)
    @Builder.Default
    private int confirmations = 0;

    // 네트워크 (SHASTA | MAINNET)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String network = "SHASTA";

    // 처리 상태: PENDING → CONFIRMED → CREDITED → FAILED
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    // 라이선스 발급 성공 여부
    @Builder.Default
    private boolean licenseIssued = false;

    // 발급된 라이선스 토큰 (성공 시)
    @Column(length = 64)
    private String issuedLicenseToken;

    // 선택한 플랜 (CORE=$7 / PRO=$13)
    @Column(length = 20)
    @Builder.Default
    private String planName = "CORE";

    // 온체인 타임스탬프 (밀리초, TronGrid block_timestamp)
    private Long onchainTimestampMs;

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    private LocalDateTime creditedAt;
}
