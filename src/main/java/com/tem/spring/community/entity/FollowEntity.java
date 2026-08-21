package com.tem.spring.community.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자 간 팔로우(Follow/Follower) 관계 엔티티 (MySQL)
 * (follower_id, following_id) 복합 유니크 인덱스로 중복 팔로우 방지
 */
@Entity
@Table(name = "follows", indexes = {
        @Index(name = "idx_follower_following", columnList = "follower_id, following_id", unique = true),
        @Index(name = "idx_following_id", columnList = "following_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private UserEntity follower; // 팔로우를 건 사람 (나)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_id", nullable = false)
    private UserEntity following; // 팔로우를 받은 사람 (대상 전문가/투자자)

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
