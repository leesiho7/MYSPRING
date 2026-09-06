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
public class DebateMessage {
    private String personaId;
    private String name;
    private String title;
    private String avatar;
    private String stance;
    private String content;
    private List<String> metrics;
    private String targetPrice;
}