package com.tem.spring.wallet.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AETHER 토큰 출금 요청 및 온체인/바이비트 영수증 증명 엔티티 (MySQL)
 */
@Entity
@Table(name = "withdrawals", indexes = {
        @Index(name = "idx_withdraw_user", columnList = "user_id"),
        @Index(name = "idx_withdraw_status", columnList = "status"),
        @Index(name = "idx_withdraw_tx", columnList = "proofTxHash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private double amount; // 출금 수량 (AETHER)

    @Column(nullable = false, length = 30)
    private String destinationType; // WEB3_WALLET, BYBIT_UID

    @Column(nullable = false, length = 120)
    private String destinationAddress; // 0x... 지갑 주소 또는 바이비트 UID

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED"; // PENDING, COMPLETED, REJECTED

    @Column(nullable = false, length = 66)
    private String proofTxHash; // 블록체인 온체인 트랜잭션 해시 (0x...) 또는 Bybit Transfer Proof ID

    @Column(nullable = false, length = 128)
    private String cryptographicProof; // 무결성 증명 서명 (SHA-256 HMAC Receipt Proof)

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;
}
