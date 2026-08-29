package com.tem.spring.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 실시간 멀티채널 뉴스 및 AI 호재/악재 감성·영향도 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RichNewsItemDto {
    private String id;
    private String symbol;
    private String category; // ALL, CRYPTO, KOREA, US_TECH, MACRO, DART
    private String categoryLabel;
    private String title;
    private String titleKo;
    private String titleCn;
    private String snippet;
    private String snippetKo;
    private String snippetCn;
    private String source;
    private String timestamp;
    private String imageUrl;
    private String sentiment; // BULLISH, BEARISH, NEUTRAL
    private double sentimentScore; // -1.0 ~ +1.0
    private String impact; // HIGH, MED, LOW
    private int impactPercent; // 0 ~ 100%
    private String link;
    private String actionGuideKo;
    private String actionGuideEn;
    private String actionGuideCn;
}
