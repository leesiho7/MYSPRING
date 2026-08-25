package com.tem.spring.stream.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 실시간 트레이딩 방송 및 금융 미디어 채널 엔티티
 */
@Entity
@Table(name = "stream_channels", indexes = {
        @Index(name = "idx_stream_category", columnList = "category"),
        @Index(name = "idx_stream_live", columnList = "isLiveNow"),
        @Index(name = "idx_stream_symbol", columnList = "targetSymbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String channelName;

    @Column(nullable = false, length = 200)
    private String streamTitle;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String streamType = "LIVE_STREAM"; // LIVE_STREAM, FINANCIAL_NEWS, VOD_TUTORIAL, SCALPING_ROOM

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String platform = "YOUTUBE"; // YOUTUBE, TWITCH, HLS_DIRECT

    @Column(nullable = false, length = 255)
    private String embedUrl; // 임베드용 동영상 ID 또는 직접 재생 URL

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String category = "CRYPTO"; // CRYPTO, US_STOCK, MACRO, QUANT

    @Column(length = 300)
    private String thumbnailUrl;

    @Column(nullable = false)
    @Builder.Default
    private int viewerCount = 1200;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String streamerName = "AETHER Financial Desk";

    @Column(nullable = false)
    @Builder.Default
    private boolean isLiveNow = true;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String liveSentiment = "BULLISH"; // BULLISH, BEARISH, NEUTRAL

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String targetSymbol = "BTCUSDT";

    @Column(nullable = false)
    @Builder.Default
    private int likeCount = 142;

    @Column(nullable = false)
    @Builder.Default
    private int dislikeCount = 6;

    @Column(nullable = false)
    @Builder.Default
    private int commentCount = 28;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
