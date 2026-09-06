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
public class AiDebateResponse {
    private String symbol;
    private LocalDateTime timestamp;
    private int consensusScore;
    private String consensusVerdict;
    private int bullRatio;
    private int bearRatio;
    private String suggestedAction;
    private String keyTakeaway;
    private Double recommendedTrailingStop;
    private String targetPriceRange;
    private List<DebateMessage> dialogue;
}