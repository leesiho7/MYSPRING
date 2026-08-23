package com.tem.spring.quant.adapter;

import com.tem.spring.core.model.Candle;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

import java.util.List;

/**
 * 수집된 표준 Candle 리스트를 ta4j의 BarSeries로 변환하는 어댑터
 */
@Component
public class BarSeriesMapper {

    public BarSeries toBarSeries(String symbol, List<Candle> candles) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(symbol)
                .withMaxBarCount(1000)
                .build();

        if (candles == null || candles.isEmpty()) {
            return series;
        }

        // 1. 타임스탬프 기준 오름차순 정렬
        List<Candle> sorted = candles.stream()
                .filter(c -> c != null && c.getTimestamp() != null)
                .sorted(java.util.Comparator.comparing(Candle::getTimestamp))
                .toList();

        java.time.ZonedDateTime lastTimestamp = null;
        for (Candle candle : sorted) {
            // 2. 중복/과거 타임스탬프 방어 (ta4j 시간순 역행 예외 방지)
            if (lastTimestamp != null && !candle.getTimestamp().isAfter(lastTimestamp)) {
                continue;
            }

            double open = candle.getOpen() > 0 ? candle.getOpen() : 1.0;
            double high = candle.getHigh() >= open ? candle.getHigh() : open;
            double low = candle.getLow() > 0 && candle.getLow() <= open ? candle.getLow() : open * 0.99;
            double close = candle.getClose() > 0 ? candle.getClose() : open;
            double volume = candle.getVolume() >= 0 ? candle.getVolume() : 100.0;

            try {
                series.addBar(
                        candle.getTimestamp(),
                        DecimalNum.valueOf(open),
                        DecimalNum.valueOf(high),
                        DecimalNum.valueOf(low),
                        DecimalNum.valueOf(close),
                        DecimalNum.valueOf(volume)
                );
                lastTimestamp = candle.getTimestamp();
            } catch (Exception ignored) {
                // ta4j 내부 엣지 케이스 시간 검증 방어
            }
        }

        return series;
    }
}
