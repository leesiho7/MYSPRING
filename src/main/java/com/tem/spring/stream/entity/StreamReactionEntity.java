package com.tem.spring.stream.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 실시간 방송/동영상 좋아요/싫어요 반응 엔티티
 */
@Entity
@Table(name = "stream_reactions", indexes = {
        @Index(name = "idx_reaction_channel_user", columnList = "channel_id, user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private StreamChannelEntity channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 10)
    private String reactionType; // LIKE, DISLIKE

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
