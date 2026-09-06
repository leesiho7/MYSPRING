package com.tem.spring.bot.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 유저별 고유 TRON(TRC-20) 입금 주소 엔티티
 * - Shasta 테스트넷 / Mainnet 겸용
 * - 유저마다 독립된 T... 주소 발급 → 폴링 감지 기반 크레딧 충전
 */
@Entity
@Table(name = "tron_deposit_addresses", indexes = {
        @Index(name = "idx_tron_addr_user",    columnList = "user_id", unique = true),
        @Index(name = "idx_tron_addr_address", columnList = "tronAddress", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TronDepositAddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    // 유저 전용 TRON 입금 주소 (T로 시작하는 Base58Check 주소)
    @Column(nullable = false, unique = true, length = 42)
    private String tronAddress;

    // 해당 주소의 HEX 형식 주소 (TronGrid API 내부용)
    @Column(length = 42)
    private String tronAddressHex;

    // 네트워크 구분 (SHASTA | MAINNET)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String network = "SHASTA";

    // 마지막으로 폴링에서 처리한 트랜잭션 ID (중복 처리 방지)
    @Column(length = 120)
    private String lastProcessedTxId;

    // 마지막 폴링 시각
    private LocalDateTime lastPolledAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
