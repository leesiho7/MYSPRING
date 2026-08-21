package com.tem.spring.core.contract;

import com.tem.spring.core.model.TimeFrame;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

/**
 * OpenBB 스타일의 공통 데이터 요청 파라미터 (Data Contract)
 */
@Value
@Builder
public class StandardHistoricalParams {
    String symbol;
    TimeFrame timeFrame;
    ZonedDateTime startDate;
    ZonedDateTime endDate;
    Integer limit;
}
