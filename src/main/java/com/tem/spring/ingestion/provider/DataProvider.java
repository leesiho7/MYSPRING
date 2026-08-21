package com.tem.spring.ingestion.provider;

import com.tem.spring.core.contract.StandardHistoricalParams;
import com.tem.spring.core.model.Candle;

import java.util.List;

/**
 * OpenBB Provider Extension 스타일의 공통 데이터 공급자 인터페이스
 */
public interface DataProvider {
    String getProviderName();

    boolean supports(String symbol);

    List<Candle> fetchHistorical(StandardHistoricalParams params);
}
