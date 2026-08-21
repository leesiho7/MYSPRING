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

    public PersonaAdvisoryService(@Autowired(required = false) VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        initSeedPrinciples();
    }

    public PersonaAdvice advise(String symbol, QuantitativeSignal quant, QualitativeInsight qual) {
        log.info("[PersonaAdvisoryService] Generating multi-persona advice for {}", symbol);

        if (vectorStore != null) {
            try {
                String buffettQuery = "Warren Buffett 가치투자 인내 공포 탐욕 안전마진";
                String simonsQuery = "Jim Simons 퀀트 수학적 우위 모멘텀 손익비 손절";
                String dalioQuery = "Ray Dalio 매크로 경제 사이클 유동성 분산투자 리스크";

                String buffett = getTopQuote(buffettQuery, fallbackBuffett(quant));
                String simons = getTopQuote(simonsQuery, fallbackSimons(quant));
                String dalio = getTopQuote(dalioQuery, fallbackDalio(qual));

                return PersonaAdvice.builder()
                        .warrenBuffett(buffett)
                        .jimSimons(simons)
                        .rayDalio(dalio)
                        .build();
            } catch (Exception e) {
                log.warn("[PersonaAdvisoryService] Persona advice search fallback: {}", e.getMessage());
            }
        }

        return fallbackPersonaAdvice(quant, qual);
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

    private PersonaAdvice fallbackPersonaAdvice(QuantitativeSignal quant, QualitativeInsight qual) {
        return PersonaAdvice.builder()
                .warrenBuffett(fallbackBuffett(quant))
                .jimSimons(fallbackSimons(quant))
                .rayDalio(fallbackDalio(qual))
                .build();
    }

    private String fallbackBuffett(QuantitativeSignal quant) {
        if (quant.getQuantScore() > 0.3) {
            return "우량한 자산이 상승 모멘텀을 탈 때 섣불리 차익 실현하기보다 복리의 힘을 믿고 보유 기간을 늘리는 것이 현명하다.";
        } else {
            return "가격이 하락한다고 공포에 질려 던지지 말고, 자산의 펀더멘털과 내재 가치가 훼손되지 않았다면 훌륭한 매수 기회로 삼아라.";
        }
    }

    private String fallbackSimons(QuantitativeSignal quant) {
        if (quant.isGoldenCross()) {
            return String.format("단기 이평선이 장기 이평선을 상향 돌파(RSI %.1f)하여 통계적 상승 우위 구간에 진입함. 손익비 1:2.5 설정 후 진입 권고.", quant.getRsi());
        } else {
            return "현재 데이터는 명확한 통계적 우위(Edge)가 약한 중립 구간임. 불필요한 매매 수수료를 아끼고 확실한 시그널 발생까지 대기하라.";
        }
    }

    private String fallbackDalio(QualitativeInsight qual) {
        return String.format("거시 시장 분석(%s)을 감안할 때, 현금 비중 20%%를 상시 확보하고 유동성 긴축 위험에 대비한 포트폴리오 헤징 전략을 병행하라.",
                qual != null ? qual.getSentiment() : "NEUTRAL");
    }
}
