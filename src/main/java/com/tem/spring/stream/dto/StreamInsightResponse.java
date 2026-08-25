package com.tem.spring.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamInsightResponse {

    private Long insightId;
    private Long channelId;
    private String channelName;
    private String symbol;
    private String signalType;
    private String commentary;
    private Double targetPrice;
    private Double stopPrice;
    private String sentiment;
    private LocalDateTime timestamp;
}
