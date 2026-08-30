package com.tem.spring.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionChartAnalysisResponse {
    private boolean success;
    private String symbol;
    private String analysisMarkdown;
    private List<String> identifiedPatterns;
    private String technicalVerdict;
    private Double supportPrice;
    private Double resistancePrice;
    private Double currentPrice;
    private String modelUsed;
    private Long processingTimeMs;
    private LocalDateTime analyzedAt;
}