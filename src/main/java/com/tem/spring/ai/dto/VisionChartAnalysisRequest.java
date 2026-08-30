package com.tem.spring.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionChartAnalysisRequest {
    private String symbol;
    private String imageBase64;
    private String prompt;
    private String timeframe;
}