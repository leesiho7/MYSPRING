package com.tem.spring.ingestion.service;

import com.tem.spring.core.contract.StandardHistoricalParams;
import com.tem.spring.core.model.Candle;
import com.tem.spring.core.model.TimeFrame;
import com.tem.spring.ingestion.provider.DataProvider;
import com.tem.spring.ingestion.provider.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenBB 스타일 수집 파이프라인의 진입 서비스: 캐싱 및 공급자 라우팅 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataIngestionService {

    private final ProviderRegistry providerRegistry;
    private final Map<String, List<Candle>> candleCache = new ConcurrentHashMap<>();

    public List<Candle> getHistoricalData(String symbol, TimeFrame timeFrame, int limit) {
        String cacheKey = symbol.toUpperCase() + "_" + (timeFrame != null ? timeFrame.getCode() : "1d") + "_" + limit;

        return candleCache.computeIfAbsent(cacheKey, key -> {
            DataProvider provider = providerRegistry.getProviderFor(symbol)
                    .orElseThrow(() -> new IllegalArgumentException("No supporting data provider found for symbol: " + symbol));

            log.info("[MarketDataIngestionService] Fetching historical data using provider: {} for {}", provider.getProviderName(), symbol);

            StandardHistoricalParams params = StandardHistoricalParams.builder()
                    .symbol(symbol)
                    .timeFrame(timeFrame != null ? timeFrame : TimeFrame.D1)
                    .limit(limit)
                    .build();

            List<Candle> fetched = provider.fetchHistorical(params);
            return fetched != null ? fetched : Collections.emptyList();
        });
    }

    public void clearCache() {
        candleCache.clear();
    }
}
