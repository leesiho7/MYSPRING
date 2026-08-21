package com.tem.spring.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 커뮤니티 및 금융 인텔리전스 유저 엔티티 (MySQL)
 * Web3 지갑 주소 매핑 및 평판 점수(Reputation) 지원
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_username", columnList = "username", unique = true),
        @Index(name = "idx_nickname", columnList = "nickname", unique = true),
        @Index(name = "idx_wallet", columnList = "walletAddress")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(length = 100)
    private String walletAddress; // Web3 ERC-20 보상용 지갑 주소 (0x...)

    @Column(length = 50)
    private String bybitUid; // 바이비트 계정 UID (수수료 0원 즉시 출금용)

    @Column(nullable = false)
    @Builder.Default
    private double tokenBalance = 50.0; // 보유 AETHER 토큰 잔고 (기본 가입 축하 50.0 지급)

    @Column(nullable = false)
    @Builder.Default
    private int reputationScore = 100; // 커뮤니티 기여도 평판 점수 (기본 100점)

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "ROLE_USER"; // ROLE_USER, ROLE_EXPERT, ROLE_ADMIN

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
