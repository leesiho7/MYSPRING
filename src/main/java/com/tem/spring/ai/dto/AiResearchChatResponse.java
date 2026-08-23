package com.tem.spring.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResearchChatResponse {
    private String reply;
    private String conversationId;
    private String symbol;
    private String intentVerdict;
    private String recommendation;
    private String positionSizingGuide;
    private String invalidationLevel;
    private double confidenceScore;
    private int entryQualityScore;
}
