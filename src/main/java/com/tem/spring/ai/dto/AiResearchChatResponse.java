package com.tem.spring.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("answer")
    public String getAnswer() {
        return this.reply;
    }

    @JsonProperty("content")
    public String getContent() {
        return this.reply;
    }

    @JsonProperty("message")
    public String getMessage() {
        return this.reply;
    }
}
