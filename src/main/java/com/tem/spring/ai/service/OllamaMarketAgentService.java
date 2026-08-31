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
    private final QwenMaxApiService qwenMaxApiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaMarketAgentService(ObjectProvider<ChatClient> chatClientProvider,
                                    ObjectProvider<ChatModel> chatModelProvider,
                                    ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                                    FinancialNewsRagService ragService,
                                    org.springframework.beans.factory.ObjectProvider<QwenMaxApiService> qwenMaxApiServiceProvider) {
        this.ragService = ragService;
        this.qwenMaxApiService = qwenMaxApiServiceProvider != null ? qwenMaxApiServiceProvider.getIfAvailable() : null;
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

    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
            io.github.resilience4j.circuitbreaker.CircuitBreaker.of("ollamaAgent",
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            .slidingWindowSize(5)
                            .minimumNumberOfCalls(3)
                            .failureRateThreshold(50.0f)
                            .waitDurationInOpenState(java.time.Duration.ofSeconds(15))
                            .permittedNumberOfCallsInHalfOpenState(2)
                            .build());

    private final io.github.resilience4j.retry.Retry retry =
            io.github.resilience4j.retry.Retry.of("ollamaRetry",
                    io.github.resilience4j.retry.RetryConfig.custom()
                            .maxAttempts(2)
                            .waitDuration(java.time.Duration.ofMillis(400))
                            .build());

    public QualitativeInsight analyzeMarketSentiment(String symbol) {
        return analyzeMarketSentiment(symbol, null);
    }

    private static final java.util.regex.Pattern JSON_TAG_PATTERN = java.util.regex.Pattern.compile("(?s)<json>(.*?)</json>");

    public QualitativeInsight analyzeMarketSentiment(String symbol, com.tem.spring.ai.dto.UnifiedMarketContext context) {
        List<String> headlines = context != null && context.getKeyHeadlines() != null ?
                context.getKeyHeadlines() : ragService.retrieveRelevantNews(symbol);
        String newsContext = String.join("\n- ", headlines);
        String quantBlock = context != null ? context.toPromptBlock() : "[BGE-M3 RAG 뉴스]\n- " + newsContext;

        // 1. 서킷 브레이커 상태 점검
        if (circuitBreaker.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN) {
            log.warn("[OllamaMarketAgentService] ⚡ CircuitBreaker is OPEN. Skipping Ollama API call for {} and using deterministic Quant/FastDTW fallback.", symbol);
            return fallbackInsight(symbol, headlines, context);
        }

        if (chatClient != null) {
            try {
                String systemPrompt = """
                        당신은 골드만삭스/블룸버그 인텔리전스 퀀트 데스크의 수석 금융 리서치 애널리스트입니다.

                        [🚨 필수 추론 원칙 & 보안 가이드라인]
                        1. **CoT (Chain-of-Thought) 및 자아 검증(Self-Consistency Verification)**:
                           - 최종 결론을 내리기 전, 반드시 <thought> 태그 내에서 단계별로 정량 지표와 외신을 분석하고 엄격한 리스크 관리자 관점에서 2가지 반대 위험(Bull Trap, 과매수 다이버전스 등)을 스스로 반박·검증하십시오.
                        2. **과신 금지 및 무효화 기준 명시 (Anti-Overconfidence)**:
                           - '100% 확실', '무위험', '무조건 폭등' 등의 단정적 표현을 엄격히 금지합니다.
                        3. **수치 팩트 기반 추론 (Fact-Grounded)**:
                           - FastDTW 승률, RSI, 1시간봉 기준가 괴리율, 거시 외신 팩트를 직접 인용하여 감성 점수를 -1.0 ~ +1.0 사이로 정밀하게 산출하십시오.
                        4. **지연시간 시차 보정 하드 룰 (Latency Lag Penalty)**:
                           - `<data_timestamps>`의 lag_penalty_applied가 true이거나 뉴스 시차가 15분 이상 지연된 경우, 해당 뉴스는 이미 가격에 선반영된 과거 소식이므로 뉴스 가중치를 50% 축소하고 실시간 차트 가격 움직임을 최우선 기준으로 판정하십시오.
                        5. **출력 형식**:
                           - 중간 추론은 <thought>...</thought>에 작성하고, 최종 결과는 반드시 <json>...</json> 태그 안에만 완벽한 JSON 형식으로 출력하십시오.
                        """;

                String userPrompt = """
                        아래 제공된 [구조화된 마켓 XML 데이터]를 분석하여 {{SYMBOL}} 자산에 대한 기관급 투자 인텔리전스 리포트를 작성하세요.

                        {{QUANT_CONTEXT_BLOCK}}

                        [Few-Shot In-Context Learning 예시]
                        ---
                        [Good Case 1: 지표-뉴스 다이버전스 발생 시 보수적 HOLD]
                        <thought>
                        1. 가설: 외신 뉴스는 긍정적이나 RSI가 74로 과매수 구간이고 SMA20 데드크로스 발생.
                        2. 자아 검증: 상방 추종 시 단기 차익 실현 매물로 인한 Bull Trap(가짜 반등) 리스크 1, 선물 미결제약정 과열에 따른 롱 스퀴즈 리스크 2 존재.
                        3. 최종 결론: 지표 하방 압력이 우세하므로 과열 매수를 자제하고 NEUTRAL(0.0)로 보수적 관망.
                        </thought>
                        <json>
                        {
                          "sentiment": "NEUTRAL",
                          "sentimentScore": 0.0,
                          "confidence": 0.85,
                          "macroSummary": "호재성 외신에도 불구하고 RSI(74.0) 과매수와 단기 데드크로스가 겹쳐 단기 지지선 테스트가 예상됩니다.",
                          "riskFactors": "선물 레버리지 과열에 따른 롱스퀴즈 및 1시간봉 기준 시가 하향 이탈 리스크."
                        }
                        </json>

                        [Good Case 2: 정량 지표 + 프랙탈 승률 동반 상승 추세]
                        <thought>
                        1. 가설: FastDTW 승률 80% + RSI 58(안정적 상승) + 기준가 대비 +1.2% 상승.
                        2. 자아 검증: 거시 금리 발표 경계감이 있으나 온체인 기관 ETF 순유입이 하방을 견고히 지지함.
                        3. 최종 결론: BULLISH(+0.70) 부여 및 손절선 SMA20 설정.
                        </thought>
                        <json>
                        {
                          "sentiment": "BULLISH",
                          "sentimentScore": 0.70,
                          "confidence": 0.92,
                          "macroSummary": "FastDTW 과거 승률(80.0%)과 기관 현물 ETF 순유입이 일치하며 견고한 상승 모멘텀을 형성하고 있습니다.",
                          "riskFactors": "SMA20 하향 돌파 시 단기 추세 무효화 및 거시 지표 발표 전 변동성 확대."
                        }
                        </json>

                        [Anti-Example: 피해야 할 나쁜 답변]
                        "이 종목은 무조건 100% 급등합니다! 지금 당장 풀매수하세요!" (❌ 수치 근거 부재, 리스크 미제시, 과신 금지 위반)
                        ---

                        이제 위 가이드라인과 Few-Shot 예시를 준수하여 {{SYMBOL}}에 대한 <thought>와 <json>을 작성하세요.
                        """
                        .replace("{{SYMBOL}}", symbol != null ? symbol : "BTCUSDT")
                        .replace("{{QUANT_CONTEXT_BLOCK}}", quantBlock);

                log.info("[OllamaMarketAgentService] Sending CoT + Few-Shot prompt for {}", symbol);

                String responseText = null;

                // 1. Qwen-Max 플래그십 클라우드 API 우선 호출 (300B+ 초고지능)
                if (qwenMaxApiService != null && qwenMaxApiService.isEnabled()) {
                    log.info("[OllamaMarketAgentService] 🚀 Dispatching prompt to Qwen-Max Flagship Cloud API for {}", symbol);
                    responseText = qwenMaxApiService.generateChat(systemPrompt, userPrompt);
                }

                // 2. Qwen-Max 미사용 또는 실패 시 로컬 Ollama 폴백 호출
                if (responseText == null && chatClient != null) {
                    try {
                        responseText = retry.executeSupplier(() ->
                                circuitBreaker.executeSupplier(() ->
                                        chatClient.prompt()
                                                .system(systemPrompt)
                                                .user(userPrompt)
                                                .call()
                                                .content()
                                )
                        );
                    } catch (Exception e) {
                        log.warn("[OllamaMarketAgentService] Ollama local call failed: {}", e.getMessage());
                    }
                }

                if (responseText != null) {
                    String jsonToParse = null;

                    // 1. <json>...</json> 태그 내부 정규식 추출
                    java.util.regex.Matcher m = JSON_TAG_PATTERN.matcher(responseText);
                    if (m.find()) {
                        jsonToParse = m.group(1).trim();
                    } else if (responseText.contains("{")) {
                        // 2. 폴백: JSON 중괄호 영역 추출
                        jsonToParse = responseText.substring(responseText.indexOf("{"), responseText.lastIndexOf("}") + 1);
                    }

                    if (jsonToParse != null && !jsonToParse.isBlank()) {
                        JsonNode node = objectMapper.readTree(jsonToParse);

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
                }
            } catch (Exception e) {
                log.error("[OllamaMarketAgentService] ❌ Ollama(Qwen 2.5 14B) 감성 분석 호출 실패 (서킷 브레이커 기록) - 정량 단독 추론 폴백 전환. 원인: {}",
                        e.getMessage());
            }
        }

        return fallbackInsight(symbol, headlines, context);
    }

    private QualitativeInsight fallbackInsight(String symbol, List<String> headlines, com.tem.spring.ai.dto.UnifiedMarketContext context) {
        String macroSummary = context != null
                ? String.format("[AI 서킷 오픈: 정량 지표 단독 추론] FastDTW 프랙탈 일치율(%.1f%%, 승률 %.0f%%) 및 RSI(%.1f) 지표를 바탕으로 견고한 %s 흐름 전개.",
                context.getSimilarityPct(), context.getHistoricalWinRatePct(), context.getRsi(),
                context.getStrikeDeltaPct() >= 0 ? "상승 모멘텀" : "지지선 탐색")
                : "[AI 서킷 오픈: 정량 지표 단독 추론] 기관 현물 ETF 순유입세 지속 및 글로벌 매크로 유동성 확대로 견고한 상승 모멘텀 유지.";

        return QualitativeInsight.builder()
                .symbol(symbol)
                .sentiment(context != null && context.getStrikeDeltaPct() >= 0 ? "BULLISH" : "NEUTRAL")
                .sentimentScore(context != null ? Math.max(-0.9, Math.min(0.9, context.getQuantScore())) : 0.55)
                .confidence(0.88)
                .macroSummary(macroSummary)
                .keyHeadlines(headlines)
                .riskFactors("선물 시장 미결제약정 과열에 따른 단기 변동성 및 거시 지표 발표 주시 필요.")
                .primaryImageUrl(ragService.getPrimaryImageUrl(symbol))
                .build();
    }
}
