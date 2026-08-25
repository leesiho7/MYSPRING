package com.tem.spring.stream.entity;

import com.tem.spring.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 실시간 방송/동영상 시청자 댓글 및 라이브 채팅 엔티티
 */
@Entity
@Table(name = "stream_comments", indexes = {
        @Index(name = "idx_comment_channel", columnList = "channel_id"),
        @Index(name = "idx_comment_time", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private StreamChannelEntity channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 50)
    private String authorNickname;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String sentimentBias = "BULLISH"; // BULLISH, BEARISH, NEUTRAL

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
