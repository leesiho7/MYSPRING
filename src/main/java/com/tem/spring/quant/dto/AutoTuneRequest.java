package com.tem.spring.quant.dto;

import com.tem.spring.core.model.TimeFrame;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoTuneRequest {
    @Builder.Default
    private String symbol = "BTCUSDT";
    @Builder.Default
    private TimeFrame timeFrame = TimeFrame.D1;
    @Builder.Default
    private int candleLimit = 150;
    @Builder.Default
    private String optimizationMetric = "SHARPE";
}