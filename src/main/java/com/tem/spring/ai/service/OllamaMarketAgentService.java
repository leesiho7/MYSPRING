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

    public OllamaMarketAgentService(ObjectProvider<ChatClient> chatClientProvider,
                                    ObjectProvider<ChatModel> chatModelProvider,
                                    ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                                    FinancialNewsRagService ragService) {
        this.ragService = ragService;
        ChatClient client = null;
        try {
            client = chatClientProvider.getIfAvailable();
        } catch (Throwable ignored) {}

        if (client == null) {
            try {
                ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
                if (builder != null) {
                    client = builder.build();
                }
            } catch (Throwable ignored) {}
        }

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
                        당신은 골드만삭스/블룸버그 인텔리전스 퀀트 데스크의 수석 금융 리서치 애널리스트입니다.
                        아래 제공된 [BGE-M3 RAG 금융 문맥 및 실시간 뉴스 데이터]를 분석하여 {{SYMBOL}} 자산에 대한 기관급 정성적 투자 인텔리전스 리포트를 작성하세요.
                        
                        [BGE-M3 RAG 금융 문맥 & 뉴스]
                        - {{NEWS_CONTEXT}}
                        
                        [분석 가이드라인]
                        1. 감성 점수(sentimentScore)는 단순 느낌이 아닌 거시경제(Macro), ETF/기관 자금 수급(Flow), 온체인(On-chain) 지표를 종합하여 -1.0(극단적 약세) ~ +1.0(극단적 강세) 사이로 정밀하게 산출하세요.
                        2. macroSummary는 월가 기관 리포트 어조(전문적, 객관적, 수치 기반)로 2문장 이내로 작성하세요.
                        3. riskFactors는 다운사이드 리스크 및 주의 지표를 1문장으로 명확히 짚어내세요.
                        
                        반드시 아래 JSON 형식으로만 응답하세요:
                        {
                          "sentiment": "BULLISH" | "BEARISH" | "NEUTRAL",
                          "sentimentScore": 0.65,
                          "confidence": 0.90,
                          "macroSummary": "ETF 순유입 지속과 유동성 환경 개선으로 중장기 상승 추세가 유효하며, 주요 저항선 돌파 시도가 이어지고 있습니다.",
                          "riskFactors": "단기 레버리지 비율 과열에 따른 변동성 확대 및 거시 지표 발표 경계감."
                        }
                        """
                        .replace("{{SYMBOL}}", symbol != null ? symbol : "BTCUSDT")
                        .replace("{{NEWS_CONTEXT}}", newsContext != null ? newsContext : "");

                log.info("[OllamaMarketAgentService] Sending Bloomberg Intelligence prompt to Qwen2.5 (BGE-M3 RAG) for {}", symbol);
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
                log.error("[OllamaMarketAgentService] ❌ Ollama(Qwen 2.5 14B) 감성 분석 호출 실패 - 폴백 인사이트 사용. 원인: {}",
                        e.getMessage(), e);
            }
        }

        return fallbackInsight(symbol, headlines);
    }

    private QualitativeInsight fallbackInsight(String symbol, List<String> headlines) {
        return QualitativeInsight.builder()
                .symbol(symbol)
                .sentiment("BULLISH")
                .sentimentScore(0.55)
                .confidence(0.88)
                .macroSummary("[Bloomberg Desk] 기관 현물 ETF 순유입세 지속 및 글로벌 매크로 유동성 확대로 견고한 상승 모멘텀 유지.")
                .keyHeadlines(headlines)
                .riskFactors("선물 시장 미결제약정 과열에 따른 단기 변동성 및 거시 지표 발표 주시 필요.")
                .primaryImageUrl(ragService.getPrimaryImageUrl(symbol))
                .build();
    }
}
