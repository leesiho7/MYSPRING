package com.tem.spring.ingestion.provider;

import com.tem.spring.core.contract.StandardHistoricalParams;
import com.tem.spring.core.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 일반 주식 및 금융 포털 데이터 수집 어댑터 (AAPL, NVDA, TSLA, 005930 등)
 */
@Slf4j
@Component
public class MockFinancialPortalProvider implements DataProvider {

    @Override
    public String getProviderName() {
        return "FINANCIAL_PORTAL_MOCK";
    }

    @Override
    public boolean supports(String symbol) {
        return symbol != null && !symbol.toUpperCase().endsWith("USDT");
    }

    @Override
    public List<Candle> fetchHistorical(StandardHistoricalParams params) {
        String symbol = params.getSymbol().toUpperCase();
        int limit = params.getLimit() != null ? params.getLimit() : 100;
        log.info("[FinancialPortalProvider] Generating historical data for equity symbol: {}", symbol);

        List<Candle> candles = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        double basePrice = symbol.contains("005930") ? 75000.0 : 180.0;

        for (int i = limit; i >= 0; i--) {
            double change = (Math.random() - 0.49) * (basePrice * 0.02);
            double open = basePrice;
            double close = open + change;
            double high = Math.max(open, close) + Math.random() * (basePrice * 0.01);
            double low = Math.min(open, close) - Math.random() * (basePrice * 0.01);
            double volume = 10000 + Math.random() * 50000;

            candles.add(Candle.builder()
                    .symbol(symbol)
                    .timestamp(now.minusDays(i))
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .build());
            basePrice = close;
        }
        return candles;
    }
}
