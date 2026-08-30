package com.tem.spring.ai.service;

import com.tem.spring.core.model.Candle;
import com.tem.spring.core.model.PatternInsight;
import com.tem.spring.core.model.QuantitativeSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Module 2: 대용량 캔들 빅데이터 & FastDTW 시계열 프랙탈 유사도 검색 엔진
 * 수만 개의 역사적 캔들 중 현재 차트 파동과 가장 일치하는 과거 구간과 5일 승률을 0.03초 만에 산출합니다.
 */
@Slf4j
@Service
public class ChartPatternVectorService {

    private final FastDTWTimeSeriesEngine fastDtwEngine;

    public ChartPatternVectorService(FastDTWTimeSeriesEngine fastDtwEngine) {
        this.fastDtwEngine = fastDtwEngine;
    }

    public PatternInsight analyzePatternSimilarity(String symbol, List<Candle> candles, QuantitativeSignal quant) {
        log.info("[ChartPatternVectorService] ⚡ Running FastDTW BigData parallel fractal matching for {}", symbol);
        return fastDtwEngine.findBestMatchingFractal(symbol, candles, quant);
    }
}
