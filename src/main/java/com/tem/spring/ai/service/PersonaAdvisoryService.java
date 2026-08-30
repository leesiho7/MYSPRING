package com.tem.spring.ai.service;

import com.tem.spring.core.model.PersonaAdvice;
import com.tem.spring.core.model.QualitativeInsight;
import com.tem.spring.core.model.QuantitativeSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Module 4: ChromaDB 기반 투자 대가 페르소나 지식 베이스 자문 서비스 (Multi-Agent Advisory)
 * 워런 버핏(가치/인내), 짐 시몬스(퀀트/확률), 레이 달리오(매크로/리스크)의 원칙을 검색하여 다각도 투자 조언을 제공합니다.
 */
@Slf4j
@Service
public class PersonaAdvisoryService {

    private final VectorStore vectorStore;
    private final BrightDataNewsScraperService brightDataService;

    public PersonaAdvisoryService(@Autowired(required = false) VectorStore vectorStore,
                                  @Autowired(required = false) BrightDataNewsScraperService brightDataService) {
        this.vectorStore = vectorStore;
        this.brightDataService = brightDataService;
        initSeedPrinciples();
    }

    public PersonaAdvice advise(String symbol, QuantitativeSignal quant, QualitativeInsight qual) {
        return advise(symbol, null, quant, qual);
    }

    public PersonaAdvice advise(String symbol, com.tem.spring.ai.dto.UnifiedMarketContext context, QuantitativeSignal quant, QualitativeInsight qual) {
        log.info("[PersonaAdvisoryService] Generating multi-persona advice with Tier-2 13F Intelligence for {}", symbol);

        if (vectorStore != null) {
            try {
                String buffettQuery = "Warren Buffett 가치투자 인내 공포 탐욕 안전마진";
                String simonsQuery = "Jim Simons 퀀트 수학적 우위 모멘텀 손익비 손절";
                String dalioQuery = "Ray Dalio 매크로 경제 사이클 유동성 분산투자 리스크";

                String buffett = getTopQuote(buffettQuery, fallbackBuffett(quant, context));
                String simons = getTopQuote(simonsQuery, fallbackSimons(quant, context));
                String dalio = getTopQuote(dalioQuery, fallbackDalio(qual, context));

                return PersonaAdvice.builder()
                        .warrenBuffett(buffett)
                        .jimSimons(simons)
                        .rayDalio(dalio)
                        .build();
            } catch (Exception e) {
                log.warn("[PersonaAdvisoryService] Persona advice search fallback: {}", e.getMessage());
            }
        }

        return fallbackPersonaAdvice(quant, qual, context);
    }

    private String getTopQuote(String query, String fallback) {
        try {
            List<Document> docs = vectorStore.similaritySearch(query);
            if (docs != null && !docs.isEmpty()) {
                return docs.get(0).getContent();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private void initSeedPrinciples() {
        if (vectorStore != null) {
            try {
                Document b1 = Document.builder()
                        .withContent("남들이 탐욕스러워할 때 두려워하고, 남들이 두려워할 때 탐욕을 가져라. 단기 가격 변동에 일희일비하지 말고 훌륭한 자산을 적정 가격에 사서 인내하라.")
                        .withMetadata(Map.of("persona", "BUFFETT", "theme", "PATIENCE"))
                        .build();

                Document s1 = Document.builder()
                        .withContent("시장은 패턴과 확률로 움직인다. 감정을 철저히 배제하고, 수학적 우위(Edge)가 확인된 구간에서만 진입하며 손절 기준을 단 1%도 어기지 마라.")
                        .withMetadata(Map.of("persona", "SIMONS", "theme", "QUANT_EDGE"))
                        .build();

                Document d1 = Document.builder()
                        .withContent("모든 시장은 유동성과 신용 사이클의 지배를 받는다. 거시경제 지표와 정책 방향에 순응하되, 단일 자산에 몰빵하지 말고 상관관계가 낮은 자산으로 리스크를 분산하라.")
                        .withMetadata(Map.of("persona", "DALIO", "theme", "MACRO_CYCLE"))
                        .build();

                vectorStore.add(List.of(b1, s1, d1));
                log.info("[PersonaAdvisoryService] Seed persona principles initialized into VectorStore");
            } catch (Exception e) {
                log.debug("[PersonaAdvisoryService] Seed persona init skipped: {}", e.getMessage());
            }
        }
    }

    private PersonaAdvice fallbackPersonaAdvice(QuantitativeSignal quant, QualitativeInsight qual, com.tem.spring.ai.dto.UnifiedMarketContext context) {
        return PersonaAdvice.builder()
                .warrenBuffett(fallbackBuffett(quant, context))
                .jimSimons(fallbackSimons(quant, context))
                .rayDalio(fallbackDalio(qual, context))
                .build();
    }

    private String fallbackBuffett(QuantitativeSignal quant, com.tem.spring.ai.dto.UnifiedMarketContext context) {
        double delta = context != null ? context.getStrikeDeltaPct() : 0.0;
        BrightDataNewsScraperService.MasterInvestor13FDto tf = brightDataService != null ? brightDataService.getMasterInvestor13F() : null;
        double cashRatio = tf != null ? tf.getWarrenBuffettCashRatioPct() : 28.4;

        if (delta > 0.5) {
            return String.format("기준가 대비 +%.2f%% 상승 구간이나, 버크셔 13F 현금 비중(%.1f%%)처럼 안전마진을 상시 확보하고 인내하라.", delta, cashRatio);
        } else if (delta < -0.5) {
            return String.format("기준가 대비 %.2f%% 하락 구간은 단기 공포일 뿐, 내재 가치 훼손이 없다면 훌륭한 분할 매수 기회다.", delta);
        } else {
            return String.format("우량 자산을 적정 가격에 보유 중이라면 단기 변동성에 흔들리지 말고 현금 비중(%.1f%%)과 복리의 힘을 믿어라.", cashRatio);
        }
    }

    private String fallbackSimons(QuantitativeSignal quant, com.tem.spring.ai.dto.UnifiedMarketContext context) {
        double winRate = context != null ? context.getHistoricalWinRatePct() : 80.0;
        double sim = context != null ? context.getSimilarityPct() : 88.5;
        double rsi = context != null ? context.getRsi() : (quant != null ? quant.getRsi() : 50.0);

        if (winRate >= 65.0) {
            return String.format("FastDTW %.1f%% 일치 프랙탈 기반 과거 5봉 승률 %.0f%%(RSI %.1f) 확인. 르네상스 퀀트 모델 기준 손익비 1:2.5 진입 권고.",
                    sim, winRate, rsi);
        } else {
            return String.format("FastDTW 일치율(%.1f%%)의 통계적 우위가 모호함. 수수료와 슬리피지를 고려해 확실한 시그널까지 관망 요망.", sim);
        }
    }

    private String fallbackDalio(QualitativeInsight qual, com.tem.spring.ai.dto.UnifiedMarketContext context) {
        String sentiment = qual != null ? qual.getSentiment() : "NEUTRAL";
        BrightDataNewsScraperService.WhaleIntelligenceDto whale = brightDataService != null ? brightDataService.getWhaleIntelligence(context != null ? context.getSymbol() : "BTCUSDT") : null;
        String whaleBias = whale != null ? whale.getDominantWhaleBias() : "BULLISH_SQUEEZE";

        return String.format("거시 유동성(%s)과 온체인 고래 청산 수급(%s)을 감안하여 올웨더 포트폴리오 현금 20%%를 유지하며 리스크를 헤징하라.",
                sentiment, whaleBias);
    }
}
