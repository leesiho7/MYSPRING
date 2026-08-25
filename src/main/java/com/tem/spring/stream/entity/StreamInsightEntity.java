package com.tem.spring.stream.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 실시간 스트리밍 방송 내 타임스탬프 기반 전문가 발언 및 알파 시그널 엔티티
 */
@Entity
@Table(name = "stream_insights", indexes = {
        @Index(name = "idx_insight_channel", columnList = "channel_id"),
        @Index(name = "idx_insight_symbol", columnList = "symbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamInsightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private StreamChannelEntity channel;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String symbol = "BTCUSDT";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String signalType = "LONG_ENTRY"; // LONG_ENTRY, SHORT_ENTRY, BREAKOUT, RISK_ALERT, MACRO_COMMENT

    @Column(nullable = false, length = 500)
    private String commentary; // 전문가 실시간 주요 발언 요약

    private Double targetPrice;
    private Double stopPrice;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String sentiment = "BULLISH";

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
