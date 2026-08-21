package com.tem.spring.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

/**
 * OpenBB 스타일의 표준 OHLCV 캔들 데이터 규격
 */
@Value
@Builder
public class Candle {
    String symbol;
    ZonedDateTime timestamp;
    double open;
    double high;
    double low;
    double close;
    double volume;
}
