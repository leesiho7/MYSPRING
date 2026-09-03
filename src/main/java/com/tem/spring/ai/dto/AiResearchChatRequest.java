package com.tem.spring.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResearchChatRequest {
    private String symbol;
    private String prompt;
    private String conversationId;
    private String intent;
    private String scope;
    private String depth;
    private String amount;
    private String horizon;
    private String mode; // "INSIGHT", "GUIDE", "CODING"
    private String language; // "ko", "en", "zh", "cn"
    private String imageUrl; // Base64 data URL or HTTP image URL for Chart Vision
    private List<ChatMessageDto> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageDto {
        private String role;
        private String content;
    }
}
