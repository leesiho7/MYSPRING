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

        for (Candle candle : candles) {
            series.addBar(
                    candle.getTimestamp(),
                    DecimalNum.valueOf(candle.getOpen()),
                    DecimalNum.valueOf(candle.getHigh()),
                    DecimalNum.valueOf(candle.getLow()),
                    DecimalNum.valueOf(candle.getClose()),
                    DecimalNum.valueOf(candle.getVolume())
            );
        }

        return series;
    }
}
