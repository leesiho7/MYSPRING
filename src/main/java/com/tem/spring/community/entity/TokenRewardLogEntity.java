package com.tem.spring.community.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Web3 ERC-20 기여도 보상 토큰(AETHER) 지급 이력 원장 엔티티
 */
@Entity
@Table(name = "token_reward_logs", indexes = {
        @Index(name = "idx_reward_recipient", columnList = "recipient_id"),
        @Index(name = "idx_reward_tx_hash", columnList = "txHash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenRewardLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private UserEntity recipient; // 보상 수령인

    @Column(nullable = false)
    private double tokenAmount; // 보상 토큰 수량 (예: 10.0 AETHER)

    @Column(nullable = false, length = 100)
    private String reason; // 보상 사유 (예: "양질의 퀀트 분석글 작성", "좋아요 10개 달성")

    @Column(nullable = false, length = 66)
    private String txHash; // 온체인 트랜잭션 해시 (0x...)

    @Column(nullable = false)
    private LocalDateTime rewardedAt;
}
