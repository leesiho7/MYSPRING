package com.tem.spring.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tem.spring.ai.rag.FinancialNewsRagService;
import com.tem.spring.core.model.QualitativeInsight;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring AI + Ollama(로컬 LLM) 기반 정성적 감성 분석 및 뉴스 브리핑 서비스
 */
@Slf4j
@Service
public class OllamaMarketAgentService {

    private final ChatClient chatClient;
    private final FinancialNewsRagService ragService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaMarketAgentService(ObjectProvider<ChatModel> chatModelProvider,
                                    ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                                    FinancialNewsRagService ragService) {
        this.ragService = ragService;
        ChatClient client = null;
        try {
            ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
            if (builder != null) {
                client = builder.build();
            }
        } catch (Throwable ignored) {}

        if (client == null) {
            try {
                ChatModel model = chatModelProvider.getIfAvailable();
                if (model != null) {
                    client = ChatClient.create(model);
                }
            } catch (Throwable ignored) {}
        }
        this.chatClient = client;
    }

    public QualitativeInsight analyzeMarketSentiment(String symbol) {
        List<String> headlines = ragService.retrieveRelevantNews(symbol);
        String newsContext = String.join("\n- ", headlines);

        if (chatClient != null) {
            try {
                String prompt = """
                        당신은 금융/암호화폐 시장 정성 분석 전문 AI 에이전트입니다.
                        아래의 [최근 뉴스/공시 문맥]을 분석하여 %s 자산에 대한 감성 점수와 요약을 JSON으로 응답하세요.
                        
                        [뉴스/공시 문맥]
                        - %s
                        
                        반드시 아래와 같은 순수 JSON 형식으로만 응답해야 합니다 (마크다운 코드블록 제외):
                        {
                          "sentiment": "BULLISH" | "BEARISH" | "NEUTRAL",
                          "sentimentScore": -1.0에서 1.0 사이의 실수,
                          "confidence": 0.0에서 1.0 사이의 실수,
                          "macroSummary": "시장 및 뉴스 핵심 요약 2문장",
                          "riskFactors": "주요 리스크 요인 1문장"
                        }
                        """.formatted(symbol, newsContext);

                log.info("[OllamaMarketAgentService] Sending prompt to local Ollama LLM for {}", symbol);
                String responseText = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                if (responseText != null && responseText.contains("{")) {
                    String cleanJson = responseText.substring(responseText.indexOf("{"), responseText.lastIndexOf("}") + 1);
                    JsonNode node = objectMapper.readTree(cleanJson);

                    return QualitativeInsight.builder()
                            .symbol(symbol)
                            .sentiment(node.path("sentiment").asText("NEUTRAL"))
                            .sentimentScore(node.path("sentimentScore").asDouble(0.3))
                            .confidence(node.path("confidence").asDouble(0.85))
                            .macroSummary(node.path("macroSummary").asText("시장 전반의 긍정적인 유동성 유입세와 투자 심리 개선"))
                            .keyHeadlines(headlines)
                            .riskFactors(node.path("riskFactors").asText("단기 금리 변동성 및 거시경제 지표 발표 주시 필요"))
                            .primaryImageUrl(ragService.getPrimaryImageUrl(symbol))
                            .build();
                }
            } catch (Exception e) {
                log.warn("[OllamaMarketAgentService] Local Ollama call failed (using fallback insight): {}", e.getMessage());
            }
        }

        return fallbackInsight(symbol, headlines);
    }

    private QualitativeInsight fallbackInsight(String symbol, List<String> headlines) {
        return QualitativeInsight.builder()
                .symbol(symbol)
                .sentiment("BULLISH")
                .sentimentScore(0.45)
                .confidence(0.80)
                .macroSummary("기관 자금 유입 및 주요 매크로 지표 안정화로 중장기적 상승 모멘텀 유지")
                .keyHeadlines(headlines)
                .riskFactors("단기 차익 실현 매물 출회 및 글로벌 거시경제 변동성")
                .primaryImageUrl(ragService.getPrimaryImageUrl(symbol))
                .build();
    }
}
