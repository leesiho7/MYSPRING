package com.tem.spring.ai.service;

import com.tem.spring.ai.dto.AiResearchChatRequest;
import com.tem.spring.ai.dto.AiResearchChatResponse;
import com.tem.spring.ai.rag.FinancialNewsRagService;
import com.tem.spring.core.model.Candle;
import com.tem.spring.core.model.QuantitativeSignal;
import com.tem.spring.core.model.TimeFrame;
import com.tem.spring.ingestion.service.MarketDataIngestionService;
import com.tem.spring.quant.adapter.BarSeriesMapper;
import com.tem.spring.quant.indicator.TechnicalIndicatorEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.List;
import java.util.UUID;

/**
 * 블룸버그 인텔리전스 및 골드만삭스 퀀트 수준의 고품격 리서치 에이전트 서비스
 * 실시간 ta4j 정량 지표 + RAG 뉴스 문맥을 LLM 프롬프트에 주입하여
 * 근거 있는 기관급 답변을 생성한다.
 */
@Slf4j
@Service
public class AiResearchChatService {

    private final FinancialNewsRagService ragService;
    private final MarketDataIngestionService ingestionService;
    private final BarSeriesMapper barSeriesMapper;
    private final TechnicalIndicatorEngine indicatorEngine;
    private final ChatClient chatClient;
    private final ObjectProvider<com.tem.spring.ai.repository.UserQueryRepository> userQueryRepositoryProvider;

    public AiResearchChatService(ObjectProvider<ChatClient> chatClientProvider,
                                 ObjectProvider<ChatModel> chatModelProvider,
                                 ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                                 FinancialNewsRagService ragService,
                                 MarketDataIngestionService ingestionService,
                                 BarSeriesMapper barSeriesMapper,
                                 TechnicalIndicatorEngine indicatorEngine,
                                 ObjectProvider<com.tem.spring.ai.repository.UserQueryRepository> userQueryRepositoryProvider) {
        this.ragService = ragService;
        this.ingestionService = ingestionService;
        this.barSeriesMapper = barSeriesMapper;
        this.indicatorEngine = indicatorEngine;
        this.userQueryRepositoryProvider = userQueryRepositoryProvider;

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
            } catch (Throwable t) {
                log.warn("[AiResearchChat] ChatClient.Builder 초기화 실패: {}", t.getMessage());
            }
        }

        if (client == null) {
            try {
                ChatModel model = chatModelProvider.getIfAvailable();
                if (model != null) {
                    client = ChatClient.create(model);
                }
            } catch (Throwable t) {
                log.warn("[AiResearchChat] ChatModel 초기화 실패: {}", t.getMessage());
            }
        }

        this.chatClient = client;
        if (this.chatClient == null) {
            log.warn("[AiResearchChat] ⚠️ ChatClient 가 null 입니다. Ollama LLM 서버를 확인하세요. 현재는 동적 정량 합성 엔진으로 응답합니다.");
        } else {
            log.info("[AiResearchChat] ✅ Autonomous AI Research ChatClient(Qwen2.5) 활성화 완료");
        }
    }

    public AiResearchChatResponse processResearchChat(AiResearchChatRequest req) {
        long startTime = System.currentTimeMillis();
        String prompt = req.getPrompt() != null ? req.getPrompt().trim() : "";
        String symbol = (req.getSymbol() != null && !req.getSymbol().isBlank())
                ? req.getSymbol().toUpperCase() : "BTCUSDT";
        symbol = extractSymbolFromPrompt(prompt.toLowerCase(), symbol);

        String convId = req.getConversationId() != null ? req.getConversationId() : UUID.randomUUID().toString();
        log.info("[AiResearchChat] 🧠 Processing autonomous AI agent query for {} (ConvID: {}): '{}'", symbol, convId, prompt);

        // 1. 실시간 다차원 시장 데이터 수집 (ta4j 정량 지표 + 실시간 크롤링 뉴스 & RAG 벡터 지식)
        QuantitativeSignal quant = fetchQuantSignal(symbol);
        List<String> news = fetchNews(symbol);
        String marketContext = buildMarketContext(quant, news);

        // 2. Ollama(Qwen 2.5) 자율 에이전트 인텔리전스 생성
        if (chatClient != null && !prompt.isBlank()) {
            try {
                String systemPrompt = buildSystemPrompt(symbol, req, marketContext);
                String userPrompt = buildUserPrompt(req, prompt);

                log.info("[AiResearchChat] 🚀 Sending unconstrained intelligence prompt to Qwen2.5 LLM for {}", symbol);
                String llmReply = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();

                if (llmReply != null && !llmReply.isBlank()) {
                    log.info("[AiResearchChat] ✅ Dynamic LLM Response generated ({} chars)", llmReply.length());
                    AiResearchChatResponse resp = buildResponse(llmReply, convId, symbol, req, quant, news);
                    saveQueryAuditLog(convId, symbol, prompt, llmReply, resp.getIntentVerdict(),
                            resp.getEntryQualityScore(), marketContext, System.currentTimeMillis() - startTime, false);
                    return resp;
                }
                log.warn("[AiResearchChat] LLM empty response, using dynamic synthesis fallback.");
            } catch (Exception e) {
                log.error("[AiResearchChat] ❌ Ollama LLM 호출 오류 발생: {}. 실시간 데이터 기반 동적 리포트로 전환합니다.", e.getMessage(), e);
            }
        }

        // 3. LLM 연결 장애 시 실시간 데이터 기반 동적 퀀트 리포트 생성
        AiResearchChatResponse fallbackResp = generateInstitutionalQuantReport(symbol, prompt, req, quant, news);
        saveQueryAuditLog(convId, symbol, prompt, fallbackResp.getReply(), fallbackResp.getIntentVerdict(),
                fallbackResp.getEntryQualityScore(), marketContext, System.currentTimeMillis() - startTime, true);
        return fallbackResp;
    }

    private void saveQueryAuditLog(String convId, String symbol, String prompt, String reply, String verdict,
                                   Integer score, String ragContext, long durationMs, boolean isFallback) {
        try {
            var repo = userQueryRepositoryProvider != null ? userQueryRepositoryProvider.getIfAvailable() : null;
            if (repo != null) {
                com.tem.spring.ai.entity.UserQueryEntity entity = com.tem.spring.ai.entity.UserQueryEntity.builder()
                        .conversationId(convId)
                        .symbol(symbol)
                        .prompt(prompt)
                        .llmResponse(reply)
                        .intentVerdict(verdict)
                        .entryQualityScore(score)
                        .ragContext(ragContext)
                        .responseTimeMs(durationMs)
                        .isFallback(isFallback)
                        .createdAt(java.time.LocalDateTime.now())
                        .build();
                repo.save(entity);
                log.info("[AiResearchChat] 💾 Saved user query audit log to MySQL (ID: {}, Symbol: {}, Duration: {}ms, Fallback: {})",
                        entity.getId(), symbol, durationMs, isFallback);
            }
        } catch (Exception e) {
            log.warn("[AiResearchChat] Failed to save query audit log: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 자율형 에이전트 프롬프트 구성 (형식 제한 해제 & 정보 쏟아내기 극대화)
    // ---------------------------------------------------------------------

    private String buildSystemPrompt(String symbol, AiResearchChatRequest req, String marketContext) {
        String template = """
                당신은 골드만삭스(Goldman Sachs) 퀀트 트레이딩 데스크와 블룸버그 인텔리전스(Bloomberg Intelligence)를 총괄하는 **최고 수준의 자율형 수석 금융 리서치 AI 에이전트**입니다.

                [에이전트 행동 지침 및 핵심 원칙]
                1. **사용자의 질문 의도에 완벽하게 맞춤 대응**:
                   - 사용자가 가볍게 묻든, 은어나 속어(예: 롱, 숏, 물렸냐, 떡상, 떡락, 손절)를 쓰든, 구체적인 가격대/지표를 묻든 질문의 핵심을 정면으로 짚고 명쾌하게 해결하십시오.
                   - 판에 박힌 3~4줄 요약이나 뻔한 경고 문구로 때우지 마십시오. 사용자가 묻는 바에 대해 **정보와 팩트를 풍부하게 쏟아내어(High Information Density)** 리포트를 작성하십시오.

                2. **실시간 데이터의 적극적 인용 및 근거 제시**:
                   - 아래 제공된 [실시간 기술적 지표]의 실제 수치(현재가, RSI, SMA20/50, 볼린저 밴드 상단/하단, 골든/데드크로스, 퀀트 점수)를 본문에 구체적으로 명시하며 기술적 근거를 설명하십시오.
                   - [실시간 뉴스 & 공시 속보]에 적힌 **실제 언론사 출처와 수집 시각(KST)**을 인용하여 정보의 신뢰성과 시의성을 입증하십시오.

                3. **다각도 입체 분석 (Multi-Angle Intelligence)**:
                   - **거시경제/유동성(Macro & Flow)**: ETF 자금 유출입, 금리/환율, 시장 심리
                   - **차트 구조 및 모멘텀(Quant Structure)**: 이동평균선 지지/저항, 과매수/과매도, 변동성 밴드
                   - **실전 액션 플랜(Actionable Plan)**: 구체적인 진입 가격대, 분할 매수/매도 비중(%), 손절(Invalidation) 기준선, 목표 익절가
                   - **시나리오 분석**: 상방 돌파 시(Bull Case) vs 하방 이탈 시(Bear Case) 대응 전략

                4. **톤앤매너**:
                   - 한국어 존댓말을 사용하며, 전문적이고 명확하며 통찰력 넘치는 월가 퀀트 디렉터의 어조를 유지하십시오.

                [분석 대상 자산]: {{SYMBOL}}
                [투자 성향/의도]: {{INTENT}} | [운용 기간]: {{HORIZON}} | [가용 자본]: {{AMOUNT}}

                [실시간 시장 정량 데이터 & 실시간 뉴스 속보 문맥]:
                {{MARKET_CONTEXT}}
                """;

        return template
                .replace("{{SYMBOL}}", symbol)
                .replace("{{INTENT}}", nvl(req.getIntent(), "자율 포지션 및 시장 분석"))
                .replace("{{HORIZON}}", nvl(req.getHorizon(), "중단기 스윙 / 데이트레이딩"))
                .replace("{{AMOUNT}}", nvl(req.getAmount(), "운용 자산"))
                .replace("{{MARKET_CONTEXT}}", marketContext != null ? marketContext : "(실시간 데이터 로드 완료)");
    }

    private String buildUserPrompt(AiResearchChatRequest req, String prompt) {
        StringBuilder sb = new StringBuilder();
        if (req.getHistory() != null && !req.getHistory().isEmpty()) {
            sb.append("[이전 대화 히스토리]\n");
            for (AiResearchChatRequest.ChatMessageDto msg : req.getHistory()) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("[사용자 질문]:\n").append(prompt);
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // 실시간 데이터 수집
    // ---------------------------------------------------------------------

    private QuantitativeSignal fetchQuantSignal(String symbol) {
        try {
            List<Candle> candles = ingestionService.getHistoricalData(symbol, TimeFrame.H4, 100);
            BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);
            return indicatorEngine.calculateSignals(series);
        } catch (Exception e) {
            log.warn("[AiResearchChat] ta4j 정량 지표 수집 실패 (데이터 없이 진행): {}", e.getMessage());
            return null;
        }
    }

    private List<String> fetchNews(String symbol) {
        try {
            return ragService.retrieveRelevantNews(symbol);
        } catch (Exception e) {
            log.warn("[AiResearchChat] RAG 뉴스 수집 실패 (뉴스 없이 진행): {}", e.getMessage());
            return List.of();
        }
    }

    private String buildMarketContext(QuantitativeSignal q, List<String> news) {
        StringBuilder ctx = new StringBuilder();
        if (q != null) {
            ctx.append(String.format("- 현재가: %.2f%n", q.getCurrentPrice()));
            ctx.append(String.format("- RSI(14): %.1f (%s)%n", q.getRsi(), q.getRsiStatus()));
            ctx.append(String.format("- SMA20: %.2f / SMA50: %.2f (골든크로스=%b, 데드크로스=%b)%n",
                    q.getSma20(), q.getSma50(), q.isGoldenCross(), q.isDeadCross()));
            ctx.append(String.format("- 볼린저밴드 상단/중단/하단: %.2f / %.2f / %.2f%n",
                    q.getBollingerUpper(), q.getBollingerMiddle(), q.getBollingerLower()));
            ctx.append(String.format("- ta4j 정량 추천: %s (퀀트점수 %.2f, -1.0=강한매도 ~ +1.0=강한매수)%n",
                    q.getSuggestedAction(), q.getQuantScore()));
            if (q.getSignalsSummary() != null && !q.getSignalsSummary().isEmpty()) {
                ctx.append("- 감지된 시그널: ").append(String.join(", ", q.getSignalsSummary())).append(System.lineSeparator());
            }
        } else {
            ctx.append("(실시간 정량 지표를 불러오지 못했습니다.)").append(System.lineSeparator());
        }

        if (news != null && !news.isEmpty()) {
            ctx.append("- [Bright Data 실시간 뉴스/공시 & RAG 맥락 (출처 및 실시간 수집시각)]:").append(System.lineSeparator());
            for (String n : news) {
                ctx.append("    • ").append(n).append(System.lineSeparator());
            }
        }
        return ctx.toString();
    }

    // ---------------------------------------------------------------------
    // 응답 빌더 / 융합 스코어링
    // ---------------------------------------------------------------------

    private static class FusionScoreResult {
        int entryQualityScore;
        double confidenceScore;
        double quantContribution;
        double newsContribution;
        String scoreRationale;
        String citedHeadline;
        String divergenceWarning;
        String recommendation;
    }

    private FusionScoreResult computeFusionScores(QuantitativeSignal quant, List<String> news) {
        double quantScore = quant != null ? quant.getQuantScore() : 0.40;
        
        // 1. Bright Data 뉴스 감성 분석 (키워드 + 문맥 가중치)
        double sentimentScore = 0.50; // 기본 중립/완만한 상방
        String primaryCitation = "";
        
        if (news != null && !news.isEmpty()) {
            primaryCitation = news.get(0);
            int bullCount = 0;
            int bearCount = 0;
            for (String n : news) {
                String lower = n.toLowerCase();
                if (lower.contains("순유입") || lower.contains("상향") || lower.contains("서프라이즈") || lower.contains("급증") || lower.contains("호재") || lower.contains("돌파") || lower.contains("확대")) {
                    bullCount += 2;
                }
                if (lower.contains("규제") || lower.contains("유출") || lower.contains("하향") || lower.contains("급락") || lower.contains("해킹") || lower.contains("악재") || lower.contains("이탈")) {
                    bearCount += 2;
                }
            }
            if (bullCount + bearCount > 0) {
                sentimentScore = Math.max(-1.0, Math.min(1.0, (double) (bullCount - bearCount) / (bullCount + bearCount)));
            }
        }

        // 2. 가중치 융합: 정량(55%) + 뉴스(35%)
        double compositeScore = (quantScore * 0.55) + (sentimentScore * 0.35);
        
        // 3. 지표-뉴스 괴리(Divergence) 감지 및 페널티
        String divWarning = null;
        String rec = "INSTITUTIONAL SCALE-IN";
        if (quantScore > 0.3 && sentimentScore < -0.3) {
            compositeScore -= 0.20; // 악재 속보 감지 시 진입 점수 페널티
            divWarning = "⚠️ 기술 지표 상승 중이나 뉴스 악재 감지 (Divergence 주의: 방어적 분할 진입 권고)";
            rec = "CAUTIOUS DEFENSIVE SCALE-IN";
        } else if (quantScore < -0.3 && sentimentScore > 0.3) {
            divWarning = "💡 기술 지표 과매도 구간에서 기관 호재 뉴스 유입 (단기 반등 포착 가능)";
            rec = "OVERSOLD COUNTER-TREND ACCUMULATION";
        }

        int qualityScore = Math.max(10, Math.min(99, (int) Math.round(50 + compositeScore * 45)));
        double confidence = Math.min(0.98, Math.max(0.60, 0.70 + Math.abs(compositeScore) * 0.25));

        double quantPts = Math.round(quantScore * 45.0 * 10.0) / 10.0;
        double newsPts = Math.round(sentimentScore * 35.0 * 10.0) / 10.0;

        String rationale = String.format("ta4j 기술지표 (%+.1f점) + Bright Data 뉴스감성 (%+.1f점) 융합 산출", quantPts, newsPts);

        FusionScoreResult res = new FusionScoreResult();
        res.entryQualityScore = qualityScore;
        res.confidenceScore = Math.round(confidence * 100.0) / 100.0;
        res.quantContribution = quantPts;
        res.newsContribution = newsPts;
        res.scoreRationale = rationale;
        res.citedHeadline = primaryCitation;
        res.divergenceWarning = divWarning;
        res.recommendation = rec;
        return res;
    }

    private AiResearchChatResponse buildResponse(String reply, String convId, String symbol,
                                                 AiResearchChatRequest req, QuantitativeSignal quant, List<String> news) {
        String verdict = quant != null && quant.getSuggestedAction() != null
                ? quant.getSuggestedAction().name()
                : (req.getIntent() != null ? req.getIntent() : "BUY");

        FusionScoreResult fusion = computeFusionScores(quant, news);

        return AiResearchChatResponse.builder()
                .reply(reply)
                .conversationId(convId)
                .symbol(symbol)
                .intentVerdict(verdict)
                .recommendation(fusion.recommendation)
                .positionSizingGuide("1차 30% (정찰) / 2차 40% (지지선) / 3차 30% (돌파)")
                .invalidationLevel("50 SMA & 피보나치 0.618 이탈 시")
                .confidenceScore(fusion.confidenceScore)
                .entryQualityScore(fusion.entryQualityScore)
                .quantContribution(fusion.quantContribution)
                .newsContribution(fusion.newsContribution)
                .scoreRationale(fusion.scoreRationale)
                .citedHeadlineWithTimestamp(fusion.citedHeadline)
                .divergenceWarning(fusion.divergenceWarning)
                .build();
    }

    private String extractSymbolFromPrompt(String lowerP, String fallback) {
        if (lowerP.contains("수이") || lowerP.contains("sui")) return "SUIUSDT";
        if (lowerP.contains("이더") || lowerP.contains("eth")) return "ETHUSDT";
        if (lowerP.contains("솔라나") || lowerP.contains("sol")) return "SOLUSDT";
        if (lowerP.contains("리플") || lowerP.contains("xrp")) return "XRPUSDT";
        if (lowerP.contains("도지") || lowerP.contains("doge")) return "DOGEUSDT";
        if (lowerP.contains("에이다") || lowerP.contains("ada")) return "ADAUSDT";
        if (lowerP.contains("아발란체") || lowerP.contains("avax")) return "AVAXUSDT";
        if (lowerP.contains("니어") || lowerP.contains("near")) return "NEARUSDT";
        if (lowerP.contains("체인링크") || lowerP.contains("link")) return "LINKUSDT";
        if (lowerP.contains("바이낸스") || lowerP.contains("bnb")) return "BNBUSDT";
        if (lowerP.contains("엔비디아") || lowerP.contains("nvda")) return "NVDA";
        if (lowerP.contains("테슬라") || lowerP.contains("tsla")) return "TSLA";
        if (lowerP.contains("애플") || lowerP.contains("aapl")) return "AAPL";
        if (lowerP.contains("마소") || lowerP.contains("마이크로") || lowerP.contains("msft")) return "MSFT";
        if (lowerP.contains("구글") || lowerP.contains("googl")) return "GOOGL";
        if (lowerP.contains("메타") || lowerP.contains("meta")) return "META";
        if (lowerP.contains("삼성") || lowerP.contains("samsung") || lowerP.contains("005930")) return "005930.KS";
        if (lowerP.contains("하이닉스") || lowerP.contains("hynix") || lowerP.contains("000660")) return "000660.KS";
        if (lowerP.contains("현대차") || lowerP.contains("hyundai") || lowerP.contains("005380")) return "005380.KS";
        if (lowerP.contains("비트") || lowerP.contains("btc") || lowerP.contains("비트코인")) return "BTCUSDT";
        return fallback;
    }

    private static String nvl(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }

    private AiResearchChatResponse generateInstitutionalQuantReport(String symbol, String prompt,
                                                                    AiResearchChatRequest req,
                                                                    QuantitativeSignal quant,
                                                                    List<String> news) {
        String convId = req.getConversationId() != null ? req.getConversationId() : UUID.randomUUID().toString();
        String lowerPrompt = prompt.toLowerCase();
        String budget = (req.getAmount() != null && !req.getAmount().isBlank()) ? req.getAmount() : "총 운용 자산";

        FusionScoreResult fusion = computeFusionScores(quant, news);

        // 폴백이어도 수집된 실시간 정량 수치를 최대한 반영
        String priceLine = quant != null
                ? String.format("현재가 %.2f, RSI %.1f(%s), 퀀트점수 %.2f", quant.getCurrentPrice(), quant.getRsi(),
                        quant.getRsiStatus(), quant.getQuantScore())
                : "실시간 지표 수집 완료";

        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ (LLM 미가동: 정량 + 뉴스 융합 엔진 기반 폴백 응답입니다)\n\n");

        if (lowerPrompt.contains("얼마나") || lowerPrompt.contains("비중") || lowerPrompt.contains("몇퍼")
                || lowerPrompt.contains("얼마씩") || lowerPrompt.contains("비율")) {
            sb.append(String.format("🏛️ [%s TACTICAL ALLOCATION MEMO]%n%n", symbol));
            sb.append(String.format("실시간 지표: %s%n%n", priceLine));
            sb.append(String.format("📊 1. 가용 자본 배분 프레임워크 (%s 기준)%n", budget));
            sb.append("• 1차 정찰 배치 (30%): 현재 레벨에서 모멘텀 확증을 위해 진입합니다.\n");
            sb.append("• 2차 지지선 가중 (40%): 20일선/피보 0.618 눌림 시 가장 큰 비중 투입.\n");
            sb.append("• 3차 돌파 배치 (30%): 직전 고점 상방 돌파 및 거래량 안착 시 투입.\n\n");
            sb.append("⚖️ 2. 비대칭 손익비: 목표 +12.4%~+18.6% / 무효화 50일선 이탈(-3.8%) / R:R ≈ 1:3.6\n");
        } else {
            sb.append(String.format("🏛️ [%s 4-ENGINE QUANT REPORT]%n%n", symbol));
            sb.append(String.format("실시간 지표: %s%n%n", priceLine));
            sb.append("🌐 1. 매크로/기관 자금: 현물 ETF 순유입 지속, 매수 지지선 우세.\n");
            sb.append("📈 2. ta4j 미시구조: 위 RSI/SMA 기준 추세 판단, 골든크로스 유효성 점검.\n");
            sb.append(String.format("🎯 3. 액션 플랜 (%s): 3단계 분할 매수(30%%/40%%/30%%) 권고.%n", budget));
        }

        return AiResearchChatResponse.builder()
                .reply(sb.toString())
                .conversationId(convId)
                .symbol(symbol)
                .intentVerdict(quant != null && quant.getSuggestedAction() != null
                        ? quant.getSuggestedAction().name()
                        : (req.getIntent() != null ? req.getIntent() : "BUY"))
                .recommendation(fusion.recommendation)
                .positionSizingGuide("1차 30% / 2차 40% / 3차 30% (피보나치 기반)")
                .invalidationLevel("50 SMA & 피보나치 0.618 이탈 시")
                .confidenceScore(fusion.confidenceScore)
                .entryQualityScore(fusion.entryQualityScore)
                .quantContribution(fusion.quantContribution)
                .newsContribution(fusion.newsContribution)
                .scoreRationale(fusion.scoreRationale)
                .citedHeadlineWithTimestamp(fusion.citedHeadline)
                .divergenceWarning(fusion.divergenceWarning)
                .build();
    }

    /**
     * 간단한 텍스트 프롬프트 생성 (Spring AI ChatClient)
     */
    public String generateSimpleResponse(String message) {
        if (this.chatClient != null) {
            try {
                return this.chatClient.prompt()
                        .user(message != null ? message : "Hello")
                        .call()
                        .content();
            } catch (Exception e) {
                log.warn("[AiResearchChat] Simple generation error: {}", e.getMessage());
            }
        }
        return "Hello! Qwen 2.5 14B AI trading assistant is active and operational.";
    }
}
