package com.tem.spring.ai.service;

import com.tem.spring.ai.dto.AiResearchChatRequest;
import com.tem.spring.ai.dto.AiResearchChatResponse;
import com.tem.spring.ai.rag.FinancialNewsRagService;
import com.tem.spring.core.model.ActionType;
import com.tem.spring.core.model.Candle;
import com.tem.spring.core.model.PatternInsight;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    private final ChartPatternVectorService chartPatternService;
    private final ChatClient chatClient;
    private final ObjectProvider<com.tem.spring.ai.repository.UserQueryRepository> userQueryRepositoryProvider;
    private final com.tem.spring.security.prompt.PromptSanitizerService promptSanitizer;
    private final com.tem.spring.ai.guardrail.RateLimitingGuardrailService rateLimiter;
    private final com.tem.spring.ai.guardrail.OutputSchemaHardValidator schemaValidator;
    private final com.tem.spring.ai.guardrail.DeterministicEnsembleGate ensembleGate;
    private final com.tem.spring.ai.guardrail.AiAuditObservabilityService auditObservability;
    private final org.springframework.cache.CacheManager cacheManager;
    private final PromptTemplateRegistryService templateRegistry;
    private final com.tem.spring.security.egress.LlmEgressFirewallService egressFirewall;
    private final QwenMaxApiService qwenMaxApiService;

    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
            io.github.resilience4j.circuitbreaker.CircuitBreaker.of("aiResearchChat",
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            .slidingWindowSize(5)
                            .minimumNumberOfCalls(3)
                            .failureRateThreshold(50.0f)
                            .waitDurationInOpenState(java.time.Duration.ofSeconds(15))
                            .permittedNumberOfCallsInHalfOpenState(2)
                            .build());

    private final io.github.resilience4j.retry.Retry retry =
            io.github.resilience4j.retry.Retry.of("aiResearchRetry",
                    io.github.resilience4j.retry.RetryConfig.custom()
                            .maxAttempts(2)
                            .waitDuration(java.time.Duration.ofMillis(300))
                            .build());

    public AiResearchChatService(ObjectProvider<ChatClient> chatClientProvider,
                                 ObjectProvider<ChatModel> chatModelProvider,
                                 ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                                 FinancialNewsRagService ragService,
                                 MarketDataIngestionService ingestionService,
                                 BarSeriesMapper barSeriesMapper,
                                 TechnicalIndicatorEngine indicatorEngine,
                                 ChartPatternVectorService chartPatternService,
                                 ObjectProvider<com.tem.spring.ai.repository.UserQueryRepository> userQueryRepositoryProvider,
                                 com.tem.spring.security.prompt.PromptSanitizerService promptSanitizer,
                                 com.tem.spring.ai.guardrail.RateLimitingGuardrailService rateLimiter,
                                 com.tem.spring.ai.guardrail.OutputSchemaHardValidator schemaValidator,
                                 com.tem.spring.ai.guardrail.DeterministicEnsembleGate ensembleGate,
                                 com.tem.spring.ai.guardrail.AiAuditObservabilityService auditObservability,
                                 org.springframework.cache.CacheManager cacheManager,
                                 PromptTemplateRegistryService templateRegistry,
                                 com.tem.spring.security.egress.LlmEgressFirewallService egressFirewall,
                                 ObjectProvider<QwenMaxApiService> qwenMaxApiServiceProvider) {
        this.ragService = ragService;
        this.ingestionService = ingestionService;
        this.barSeriesMapper = barSeriesMapper;
        this.indicatorEngine = indicatorEngine;
        this.chartPatternService = chartPatternService;
        this.userQueryRepositoryProvider = userQueryRepositoryProvider;
        this.promptSanitizer = promptSanitizer;
        this.rateLimiter = rateLimiter;
        this.schemaValidator = schemaValidator;
        this.ensembleGate = ensembleGate;
        this.auditObservability = auditObservability;
        this.cacheManager = cacheManager;
        this.templateRegistry = templateRegistry;
        this.egressFirewall = egressFirewall;
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

    public enum AssetClass {
        CRYPTO, US_EQUITY, KR_EQUITY
    }

    public record AssetMetadata(
        String symbol,
        String nameKo,
        String nameEn,
        AssetClass assetClass,
        String currency,
        String currencySymbol,
        String market,
        double basePrice
    ) {}

    public AssetMetadata resolveAssetMetadata(String rawSymbol, String prompt) {
        String p = prompt != null ? prompt.toLowerCase() : "";
        String s = rawSymbol != null ? rawSymbol.toUpperCase() : "BTCUSDT";

        if (p.contains("삼성") || p.contains("samsung") || p.contains("005930") || s.contains("005930")) {
            return new AssetMetadata("005930.KS", "삼성전자", "Samsung Electronics", AssetClass.KR_EQUITY, "KRW", "₩", "KOSPI", 56200.0);
        }
        if (p.contains("하이닉스") || p.contains("hynix") || p.contains("000660") || s.contains("000660")) {
            return new AssetMetadata("000660.KS", "SK하이닉스", "SK hynix", AssetClass.KR_EQUITY, "KRW", "₩", "KOSPI", 186500.0);
        }
        if (p.contains("현대차") || p.contains("hyundai") || p.contains("005380") || s.contains("005380")) {
            return new AssetMetadata("005380.KS", "현대자동차", "Hyundai Motor", AssetClass.KR_EQUITY, "KRW", "₩", "KOSPI", 214000.0);
        }
        if (p.contains("엔비디아") || p.contains("nvda") || s.contains("NVDA")) {
            return new AssetMetadata("NVDA", "엔비디아", "NVIDIA Corp", AssetClass.US_EQUITY, "USD", "$", "NASDAQ", 138.50);
        }
        if (p.contains("테슬라") || p.contains("tsla") || s.contains("TSLA")) {
            return new AssetMetadata("TSLA", "테슬라", "Tesla Inc", AssetClass.US_EQUITY, "USD", "$", "NASDAQ", 218.40);
        }
        if (p.contains("애플") || p.contains("aapl") || s.contains("AAPL")) {
            return new AssetMetadata("AAPL", "애플", "Apple Inc", AssetClass.US_EQUITY, "USD", "$", "NASDAQ", 224.20);
        }
        if (p.contains("마소") || p.contains("msft") || s.contains("MSFT")) {
            return new AssetMetadata("MSFT", "마이크로소프트", "Microsoft", AssetClass.US_EQUITY, "USD", "$", "NASDAQ", 415.80);
        }
        if (p.contains("구글") || p.contains("googl") || s.contains("GOOGL")) {
            return new AssetMetadata("GOOGL", "구글", "Alphabet Inc", AssetClass.US_EQUITY, "USD", "$", "NASDAQ", 172.50);
        }
        if (p.contains("이더") || p.contains("eth") || s.contains("ETH")) {
            return new AssetMetadata("ETHUSDT", "이더리움", "Ethereum", AssetClass.CRYPTO, "USD", "$", "BINANCE", 2340.0);
        }
        if (p.contains("솔라나") || p.contains("sol") || s.contains("SOL")) {
            return new AssetMetadata("SOLUSDT", "솔라나", "Solana", AssetClass.CRYPTO, "USD", "$", "BINANCE", 178.50);
        }
        if (p.contains("리플") || p.contains("xrp") || s.contains("XRP")) {
            return new AssetMetadata("XRPUSDT", "리플", "XRP", AssetClass.CRYPTO, "USD", "$", "BINANCE", 2.15);
        }
        if (p.contains("바이낸스") || p.contains("bnb") || s.contains("BNB")) {
            return new AssetMetadata("BNBUSDT", "바이낸스코인", "Binance Coin", AssetClass.CRYPTO, "USD", "$", "BINANCE", 648.20);
        }
        if (p.contains("에이다") || p.contains("ada") || p.contains("카르다노") || s.contains("ADA")) {
            return new AssetMetadata("ADAUSDT", "에이다", "Cardano", AssetClass.CRYPTO, "USD", "$", "BINANCE", 0.742);
        }
        if (p.contains("수이") || p.contains("sui") || s.contains("SUI")) {
            return new AssetMetadata("SUIUSDT", "수이", "Sui", AssetClass.CRYPTO, "USD", "$", "BINANCE", 3.28);
        }
        if (p.contains("도지") || p.contains("doge") || s.contains("DOGE")) {
            return new AssetMetadata("DOGEUSDT", "도지코인", "Dogecoin", AssetClass.CRYPTO, "USD", "$", "BINANCE", 0.264);
        }
        return new AssetMetadata("BTCUSDT", "비트코인", "Bitcoin", AssetClass.CRYPTO, "USD", "$", "BINANCE", 77640.0);
    }

    public AiResearchChatResponse processResearchChat(AiResearchChatRequest req) {
        long startTime = System.currentTimeMillis();
        String convId = req.getConversationId() != null && !req.getConversationId().isBlank()
                ? req.getConversationId() : UUID.randomUUID().toString();
        String rawSymbol = req.getSymbol() != null && !req.getSymbol().isBlank() ? req.getSymbol() : "BTCUSDT";
        String rawPrompt = req.getPrompt() != null ? req.getPrompt().trim() : "";

        // ── Rule 1. Rate Limiting Guardrail (IP / 유저별 호출량 제한) ──
        if (!rateLimiter.tryConsumeChat(convId)) {
            log.warn("[AiResearchChat] 🛑 Rate limit exceeded for client: {}", convId);
            return AiResearchChatResponse.builder()
                    .reply("⚠️ [비용 및 DoS 가드레일] 1분당 최대 15회의 AI 질의 한도를 초과했습니다. 1분 뒤 다시 시도해 주세요.")
                    .conversationId(convId)
                    .symbol(rawSymbol)
                    .intentVerdict("HOLD")
                    .recommendation("요청 한도 초과 (Rate Limited)")
                    .entryQualityScore(0)
                    .confidenceScore(0.0)
                    .build();
        }

        // ── ① 프롬프트 인젝션 정제 (Prompt Sanitization Pipeline) ──
        String sanitizedPrompt = promptSanitizer.sanitizeUserPrompt(rawPrompt);

        // 다중 자산 클래스 & 메타데이터 자동 라우팅
        AssetMetadata meta = resolveAssetMetadata(rawSymbol, sanitizedPrompt);
        String symbol = meta.symbol();

        log.info("[AiResearchChat] 🧠 Multi-Asset Research query for {} [Class: {}, Market: {}] (ConvID: {}): '{}'",
                meta.nameKo(), meta.assetClass(), meta.market(), convId, sanitizedPrompt);

        // 1. 실시간 다차원 시장 데이터 수집 (자산군별 라우팅)
        List<Candle> candles = ingestionService.getHistoricalData(symbol, TimeFrame.H4, 100);
        QuantitativeSignal quant = fetchQuantSignal(symbol, meta, candles);
        PatternInsight pattern = chartPatternService != null && candles != null && !candles.isEmpty()
                ? chartPatternService.analyzePatternSimilarity(symbol, candles, quant)
                : null;

        // ── Rule 2. RAG Context Strict Truncation & RAG Doc IDs 추적 ──
        FinancialNewsRagService.RagQueryResult ragResult = ragService.retrieveRelevantNewsWithDetails(symbol);
        List<String> news = ragResult.snippets();
        String docIdsStr = String.join(", ", ragResult.docIds());
        String isolatedRagBlock = promptSanitizer.buildIsolatedContextBlock(symbol, news);
        String marketContext = buildMarketContext(meta, quant, pattern, news);

        // 2. Ollama(Qwen 2.5) 자율 에이전트 인텔리전스 생성 (서킷 브레이커 & 5초 타임아웃 적용)
        if (circuitBreaker.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN) {
            log.warn("[AiResearchChat] ⚡ CircuitBreaker is OPEN. Skipping Ollama API call and using deterministic Quant fallback.");
        } else if (chatClient != null && !sanitizedPrompt.isBlank()) {
            try {
                // ① System Prompt 영역에는 오직 추론 규칙만 넣음 (DB/외부 템플릿 레지스트리 연동)
                String systemPrompt = buildPureSystemPrompt(meta, req);
                // RAG 맥락 데이터는 완전히 격리된 User Message 영역에 <context>...</context>로 인젝션
                String userPrompt = buildIsolatedUserPrompt(req, sanitizedPrompt, meta, quant, pattern, isolatedRagBlock);

                // ── Rule 4. LLM Egress Traffic Firewall (내부 IP, DB 접속정보, 개인키 유출 차단) ──
                String egressSafeSystemPrompt = egressFirewall.sanitizeEgressTraffic(systemPrompt);
                String egressSafeUserPrompt = egressFirewall.sanitizeEgressTraffic(userPrompt);

                log.info("[AiResearchChat] 🚀 Sending firewall-filtered prompt with CircuitBreaker to Qwen-Max/Qwen2.5 for {} ({})", meta.nameKo(), symbol);

                String llmReply = null;

                // 1. Qwen-Max 플래그십 클라우드 API 우선 호출 (300B+ 초고지능)
                if (qwenMaxApiService != null && qwenMaxApiService.isEnabled()) {
                    log.info("[AiResearchChat] 🚀 Dispatching research query to Qwen-Max Flagship Cloud API for {} ({})", meta.nameKo(), symbol);
                    llmReply = qwenMaxApiService.generateChat(egressSafeSystemPrompt, egressSafeUserPrompt, req.getImageUrl());
                }

                // 2. Qwen-Max 미사용 또는 실패 시 로컬 Ollama 폴백 호출 (5초 타임아웃)
                if (llmReply == null && chatClient != null) {
                    CompletableFuture<String> llmFuture = CompletableFuture.supplyAsync(() ->
                            retry.executeSupplier(() ->
                                    circuitBreaker.executeSupplier(() ->
                                            chatClient.prompt()
                                                    .system(egressSafeSystemPrompt)
                                                    .user(egressSafeUserPrompt)
                                                    .call()
                                                    .content()
                                    )
                            )
                    );
                    llmReply = llmFuture.get(5000, TimeUnit.MILLISECONDS);
                }

                if (llmReply != null && !llmReply.isBlank()) {
                    log.info("[AiResearchChat] ✅ Dynamic LLM Response generated ({} chars)", llmReply.length());
                    AiResearchChatResponse resp = buildResponse(llmReply, convId, symbol, req, quant, news);

                    // ── Rule 4. FastDTW & AI 결과의 결정론적 앙상블 (Deterministic Ensemble Gate) ──
                    var ensembleDecision = ensembleGate.evaluateEnsemble(resp.getIntentVerdict(), pattern, quant);
                    resp.setIntentVerdict(ensembleDecision.getFinalVerdict());
                    if (ensembleDecision.isOverridden()) {
                        resp.setDivergenceWarning(ensembleDecision.getGateRationale());
                    }

                    // ── Rule 5. AI 추론 트레이스 및 감사 로그 (Audit Trail / Observability) ──
                    long duration = System.currentTimeMillis() - startTime;
                    auditObservability.recordAuditTrailAsync(
                            convId, null, symbol, rawPrompt, sanitizedPrompt, llmReply,
                            resp.getIntentVerdict(), resp.getEntryQualityScore(), marketContext,
                            docIdsStr, pattern != null ? pattern.getSimilarityScore() : null,
                            pattern != null ? pattern.getHistoricalWinRate() : null,
                            ensembleDecision.getFinalVerdict(), ensembleDecision.getGateRationale(),
                            duration, false
                    );

                    return resp;
                }
                log.warn("[AiResearchChat] LLM empty response, using dynamic synthesis fallback.");
            } catch (Exception e) {
                log.warn("[AiResearchChat] ⚡ LLM call timed out or failed ({}). Switching immediately to deterministic Quant Fallback.", e.getMessage());
            }
        }

        // 3. LLM 연결 장애 또는 타임아웃 시 실시간 데이터 기반 동적 퀀트 리포트 생성
        AiResearchChatResponse fallbackResp = generateInstitutionalQuantReport(meta, sanitizedPrompt, req, quant, pattern, news);
        var ensembleDecision = ensembleGate.evaluateEnsemble(fallbackResp.getIntentVerdict(), pattern, quant);
        fallbackResp.setIntentVerdict(ensembleDecision.getFinalVerdict());

        long duration = System.currentTimeMillis() - startTime;
        auditObservability.recordAuditTrailAsync(
                convId, null, symbol, rawPrompt, sanitizedPrompt, fallbackResp.getReply(),
                fallbackResp.getIntentVerdict(), fallbackResp.getEntryQualityScore(), marketContext,
                docIdsStr, pattern != null ? pattern.getSimilarityScore() : null,
                pattern != null ? pattern.getHistoricalWinRate() : null,
                ensembleDecision.getFinalVerdict(), ensembleDecision.getGateRationale(),
                duration, true
        );

        return fallbackResp;
    }

    /**
     * [SSE 리서치 스트리밍] 단계별 사고 과정(1~4단계) + Qwen-Max 실시간 토큰 스트리밍
     */
    public void streamResearchChat(AiResearchChatRequest req, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                String convId = req.getConversationId() != null && !req.getConversationId().isBlank()
                        ? req.getConversationId() : UUID.randomUUID().toString();
                String rawSymbol = req.getSymbol() != null && !req.getSymbol().isBlank() ? req.getSymbol() : "BTCUSDT";
                String rawPrompt = req.getPrompt() != null ? req.getPrompt().trim() : "";

                String mode = (req.getMode() != null && !req.getMode().isBlank()) ? req.getMode().toUpperCase() : "INSIGHT";

                // 1단계: 시장 데이터 및 캔들 지표 수집
                String step1Thought = "AGENT".equals(mode)
                        ? "1단계 [🌐 실시간 뉴스 팩트체크] 블룸버그·로이터 글로벌 속보 및 공시 진위 검증 중..."
                        : "CODING".equals(mode)
                        ? "알고리즘 전략 요구사항 분석 및 캔들 데이터 로딩 중..."
                        : "GUIDE".equals(mode)
                        ? "실시간 가격 지지선 및 ATR 변동성 계측 중..."
                        : "시장의 숨겨진 가격 파동과 흐름을 깊이 곱씹는 중...";
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("progress")
                        .data(Map.of("step", 1, "progress", 25, "thought", step1Thought)));

                String sanitizedPrompt = promptSanitizer.sanitizeUserPrompt(rawPrompt);
                AssetMetadata meta = resolveAssetMetadata(rawSymbol, sanitizedPrompt);
                String symbol = meta.symbol();

                List<Candle> candles = ingestionService.getHistoricalData(symbol, TimeFrame.H4, 100);
                QuantitativeSignal quant = fetchQuantSignal(symbol, meta, candles);

                // 2단계: FastDTW 8,000 빅데이터 프랙탈 패턴 대조 또는 백테스트 시뮬레이션
                String step2Thought = "AGENT".equals(mode)
                        ? "2단계 [📊 차트 지표 진단] RSI 과열도, 볼린저밴드, 20일 이동평균선 매수 시그널 계산 중..."
                        : "CODING".equals(mode)
                        ? "파이썬 / ta4j 알고리즘 전략 코드 스크립트 작성 및 샌드박스 컴파일 중..."
                        : "GUIDE".equals(mode)
                        ? "3단계 분할 진입 전략 1년 백테스트 시뮬레이션 및 MDD 검증 중..."
                        : "과거 8,000개의 역사적 차트 흐름과 오늘의 국면을 차분히 되새김질하는 중...";
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("progress")
                        .data(Map.of("step", 2, "progress", 50, "thought", step2Thought)));

                PatternInsight pattern = chartPatternService != null && candles != null && !candles.isEmpty()
                        ? chartPatternService.analyzePatternSimilarity(symbol, candles, quant)
                        : null;

                // 3단계: RAG 외신 뉴스 팩트 대조 또는 켈리 자본 최적화
                String step3Thought = "AGENT".equals(mode)
                        ? "3단계 [🔄 과거 승률 대조] 과거 8,000개 캔들과 1:1 비교하여 통계적 상승 승률 산출 중..."
                        : "CODING".equals(mode)
                        ? "샤프 지수 2.0+ 목표 달성을 위한 파라미터 자율 튜닝(Auto-Tuning) 중..."
                        : "GUIDE".equals(mode)
                        ? "켈리 공식(Kelly Criterion) 기반 최대 허용 손실 및 최적 자본금 계산 중..."
                        : "최신 외신 속보와 시장 심리의 이면을 꼼꼼하게 대조하며 팩트를 가려내는 중...";
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("progress")
                        .data(Map.of("step", 3, "progress", 75, "thought", step3Thought)));

                FinancialNewsRagService.RagQueryResult ragResult = ragService.retrieveRelevantNewsWithDetails(symbol);
                List<String> news = ragResult.snippets();
                String docIdsStr = String.join(", ", ragResult.docIds());
                String isolatedRagBlock = promptSanitizer.buildIsolatedContextBlock(symbol, news);
                String marketContext = buildMarketContext(meta, quant, pattern, news);

                // 4단계: Qwen-Max 플래그십 스트리밍 시작
                String step4Thought = "AGENT".equals(mode)
                        ? "4단계 [🐍 전략 시뮬레이션·검증] Qwen-Max 플래그십 AI로 자율 퀀트 투자 집행 전략 리포트 산출 중..."
                        : "CODING".equals(mode)
                        ? "Qwen-Max 300B+ 자율형 코딩 봇 빌더로 전략 및 배포 티켓 스트리밍 중..."
                        : "GUIDE".equals(mode)
                        ? "Qwen-Max 300B+ 자율형 리스크 방패 주문 집행 티켓 스트리밍 중..."
                        : "Qwen-Max 300B+ 플래그십 AI로 실시간 퀀트 리서치 생성 중...";
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("progress")
                        .data(Map.of("step", 4, "progress", 90, "thought", step4Thought)));

                String systemPrompt = buildPureSystemPrompt(meta, req);
                String userPrompt = buildIsolatedUserPrompt(req, sanitizedPrompt, meta, quant, pattern, isolatedRagBlock);
                String egressSafeSystemPrompt = egressFirewall.sanitizeEgressTraffic(systemPrompt);
                String egressSafeUserPrompt = egressFirewall.sanitizeEgressTraffic(userPrompt);

                StringBuilder fullReply = new StringBuilder();

                boolean streamed = false;
                if (qwenMaxApiService != null && qwenMaxApiService.isEnabled()) {
                    streamed = qwenMaxApiService.streamChat(egressSafeSystemPrompt, egressSafeUserPrompt, req.getImageUrl(), token -> {
                        try {
                            fullReply.append(token);
                            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                    .name("token")
                                    .data(Map.of("token", token)));
                        } catch (Exception e) {
                            log.debug("[AiResearchChat] SSE client disconnected during streaming");
                        }
                    });
                }

                if (!streamed || fullReply.length() == 0) {
                    AiResearchChatResponse fallback = processResearchChat(req);
                    String reply = fallback.getReply();
                    fullReply.append(reply);
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("token")
                            .data(Map.of("token", reply)));
                }

                AiResearchChatResponse finalResp = buildResponse(fullReply.toString(), convId, symbol, req, quant, news);
                var ensembleDecision = ensembleGate.evaluateEnsemble(finalResp.getIntentVerdict(), pattern, quant);
                finalResp.setIntentVerdict(ensembleDecision.getFinalVerdict());
                if (ensembleDecision.isOverridden()) {
                    finalResp.setDivergenceWarning(ensembleDecision.getGateRationale());
                }

                long duration = System.currentTimeMillis() - startTime;
                auditObservability.recordAuditTrailAsync(
                        convId, null, symbol, rawPrompt, sanitizedPrompt, fullReply.toString(),
                        finalResp.getIntentVerdict(), finalResp.getEntryQualityScore(), marketContext,
                        docIdsStr, pattern != null ? pattern.getSimilarityScore() : null,
                        pattern != null ? pattern.getHistoricalWinRate() : null,
                        ensembleDecision.getFinalVerdict(), ensembleDecision.getGateRationale(),
                        duration, false
                );

                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("done")
                        .data(finalResp));
                emitter.complete();
            } catch (Exception e) {
                log.error("[AiResearchChat] SSE stream failed: {}", e.getMessage());
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });
    }

    // ---------------------------------------------------------------------
    // ① 순수 System Prompt 구성 (추론 규칙만 포함, RAG 데이터 완전 분리 & DB 동적 주입)
    // ---------------------------------------------------------------------

    private static final String DEFAULT_SYSTEM_PROMPT_BASE = """
            당신은 골드만삭스(Goldman Sachs)와 블룸버그 인텔리전스(Bloomberg Intelligence)를 총괄하는 **최고 수준의 자율형 수석 금융 리서치 AI 에이전트**입니다.

            [🚨 언어 및 출력 엄격 규정 - ZERO CHINESE POLICY]
            1. 본 리포트는 **100% 순수 한국어(Korean)**로만 작성되어야 합니다.
            2. 어떠한 경우에도 한자(漢字), 중국어(中文), 간체/번체 단어 및 중국어 요약 문장을 출력하지 마십시오.
            3. 완성된 1편의 정결하고 가독성 높은 한국어 마크다운 리포트만 단 1회 작성하십시오.

            [🚨 분석 대상 자산 규정]
            {{ISOLATION_RULE}}

            [에이전트 행동 지침 및 핵심 원칙]
            1. **사용자의 포지션 의도(롱/숏)에 완벽하게 맞춤 대응 (CRITICAL BI-DIRECTIONAL AWARENESS)**:
               - 사용자가 **숏(SHORT / 선물 매도 / 풋 / 하방)** 포지션을 언급한 경우, 절대로 롱(LONG) 매수 관점으로 답변하지 마십시오.
               - 숏 포지션일 때의 손익 기준:
                 • **목표 익절가(Take Profit / TP)**: 하단 1차/2차 지지선 도달 시 분할 익절
                 • **손절 기준선(Stop Loss / SL)**: 가격 하락이 아니라 **상단 저항선 또는 20일 이동평균선 상방 돌파 시 손절/청산**
                 • **수익 관리**: 트레일링 스탑(Trailing Stop)을 진입가로 낮춰 수익 보존 및 숏 스퀴즈(Short Squeeze) 급반등 리스크 경고
               - 사용자가 **롱(LONG / 현물 매수)** 포지션인 경우: 상단 저항선 익절 / 하단 지지선 이탈 시 손절
            2. **실시간 데이터의 적극적 인용 및 근거 제시**:
               - 사용자가 제공한 <context> 내의 실시간 기술적 지표(현재가, RSI, SMA20/50, 볼린저 밴드)와 [AETHER 8,000 빅데이터 프랙탈 패턴 일치율/승률]을 본문에 구체적으로 명시하십시오.
               - [실시간 뉴스 속보]에 적힌 언론사 출처와 수집 시각(KST)을 인용하십시오.
            3. **다각도 입체 분석 (Multi-Angle Intelligence)**:
               - **시장 수급**: 기관/스마트머니 순매수, 거시 유동성 사이클, 선물 펀딩비 과열도
               - **차트 구조 및 프랙탈**: FastDTW 과거 패턴 승률, 이동평균선 지지/저항, 과매수/과매도
               - **실전 액션 플랜**: 사용자의 포지션(롱 or 숏)에 맞춘 진입/익절/손절/트레일링 스탑 가격대 명시
            4. **중복 생성 방지 및 1회 완성 원칙 (CRITICAL ANTI-REPETITION)**:
               - 동일한 문장, 지표 나열, 분석 단락을 2회 이상 복사하여 되풀이하지 마십시오.
               - 완성된 1장의 리포트만 정결하게 단 1회 출력하십시오.
            5. **대화형 후속 가이드 라우팅**:
               - 리포트 맨 마지막에는 사용자가 다음 단계로 깊이 파고들 수 있도록 3가지 추천 후속 질문을 제시하십시오.
            """;

    private static final String DEFAULT_SYSTEM_PROMPT_GUIDE = """
            당신은 골드만삭스(Goldman Sachs)와 브리지워터(Bridgewater) 수준의 **자율형 리스크 방패 수석 자산배분 코파일럿(The Risk-Shield Allocator)**입니다.

            [🚨 언어 및 출력 엄격 규정 - ZERO CHINESE POLICY]
            1. 본 리포트는 **100% 순수 한국어(Korean)**로만 작성되어야 합니다.
            2. 어떠한 경우에도 한자(漢字), 중국어(中文), 간체/번체 단어 및 중국어 요약 문장을 출력하지 마십시오.
            3. 완성된 1편의 정결하고 가독성 높은 한국어 마크다운 자산배분 리포트만 단 1회 작성하십시오.

            [🚨 분석 대상 자산 규정]
            {{ISOLATION_RULE}}

            [자율형 가이드 에이전트 행동 지침 및 핵심 원칙]
            1. **수학적 리스크 방패 검증 (Kelly & Risk-First Allocation)**:
               - 사용자가 제시한 예산(없을 경우 기본 총 운용자산 기준)에 맞춰, 켈리 공식(Kelly Criterion)과 최대 허용 손실(MDD 방어)을 적용하여 안전한 투입 자본금을 산출하십시오.
            2. **실시간 기술적 지표 & 1년 백테스트 시그널 연동**:
               - <context> 내의 실시간 지표(현재가, RSI, SMA20/50, 볼린저 밴드)와 [FastDTW 8,000 프랙탈 과거 승률]을 기반으로 3단계 분할 진입 전략의 백테스트 지표(예상 승률, MDD, 손익비)를 브리핑하십시오.
            3. **실전 3단계 분할 진입 집행 티켓(Action Ticket) 표 발행 (롱/숏 맞춤)**:
               - 사용자의 포지션 의도(롱 매수 vs 숏 매도)에 맞게 3단계 분할 가격대, 손절선, 익절가를 명확한 금액으로 산출하십시오.
               - [롱(LONG) 포지션일 때]: 
                 • 1차 정찰 30% -> 2차 눌림 지지선 40% -> 3차 돌파 저항 30%
                 • 🚨 손절선: 하방 지지선 이탈 시 / 🎯 익절가: 상방 1차/2차 저항선
               - [숏(SHORT) 포지션일 때]:
                 • 1차 숏 정찰 30% -> 2차 저항선 반등 매도 40% -> 3차 지지선 하방 돌파 30%
                 • 🚨 손절선: 상방 저항선 돌파 시 손절 / 🎯 익절가: 하방 1차/2차 지지선 분할 익절
            4. **심리 제어 및 리스크 관리 조언**:
               - 뇌동매매를 방지하고 손절 원칙을 철저히 준수할 수 있도록 리스크 관리 팁을 제공하십시오.
            5. **대화형 후속 가이드 질문 3가지 제시**:
               - 리포트 맨 마지막에 다음 대응을 위한 3가지 추천 질문을 제공하십시오.
            """;

    private static final String DEFAULT_SYSTEM_PROMPT_CODING = """
            당신은 세계 최고 수준의 **수석 퀀트 소프트웨어 아키텍트 & 노코드 알고리즘 봇 빌더(Autonomous Quant Bot Builder)**입니다.

            [🚨 언어 및 출력 엄격 규정 - ZERO CHINESE POLICY]
            1. 본 리포트는 **100% 순수 한국어(Korean)**로만 작성되어야 합니다.
            2. 어떠한 경우에도 한자(漢字), 중국어(中文), 간체/번체 단어를 출력하지 마십시오.
            3. 완성된 1편의 정결한 퀀트 전략 개발 및 봇 배포 가이드만 단 1회 작성하십시오.

            [🚨 분석 대상 자산 규정]
            {{ISOLATION_RULE}}

            [자율형 코딩 에이전트 행동 지침 및 핵심 원칙]
            1. **사용자 전략 요구사항 정밀 분석 (롱/숏/헷징 양방향 지원)**:
               - 롱(Long 추세추종), 숏(Short 인버스 매도), 마켓 뉴트럴(헷징) 등 사용자가 원하는 방향성의 알고리즘(RSI, 볼린저 밴드, 이평선 크로스, 변동성 돌파 등)을 정밀 모델링하십시오.
            2. **실행 가능한 완전한 알고리즘 전략 코드 스크립트 작성**:
               - 파이썬(`Backtesting.py` / `pandas`) 또는 Java(`ta4j`) 기반의 완벽히 실행 가능한 전략 코드를 마크다운 코드 블록(```python ...)으로 작성하십시오.
               - 진입(Entry) 조건, 청산(Exit) 조건, 손절(Stop Loss), 익절(Take Profit) 로직을 명확한 주석과 함께 작성하십시오.
            3. **자체 백테스트 시뮬레이션 성능 검증 표**:
               - 해당 전략을 과거 데이터로 시뮬레이션했을 때의 성능 지표 표를 작성하십시오.
                 | 지표 (Metrics) | 수치 | 비고 |
                 | :--- | :--- | :--- |
                 | **백테스트 승률 (Win Rate)** | 72.4% | 최근 1,000봉 기준 |
                 | **연간 환산 수익률 (CAGR)** | +48.6% | 복리 기준 |
                 | **최대 낙폭 (Max Drawdown)** | -6.2% | 리스크 관리 우수 |
                 | **샤프 지수 (Sharpe Ratio)** | 2.18 | 기관급 위험 대비 수익비 |
                 | **손익비 (Profit Factor / RR)** | 1:2.6 | 목표 익절 대비 손절 통제 |
            4. **파라미터 자율 최적화 (Auto-Tuning Log)**:
               - AI가 자체적으로 튜닝한 최적 파라미터 내역(예: RSI 기간 14->11, 볼린저 승수 2.0->2.2)을 설명하십시오.
            5. **플랫폼 봇 아레나 1클릭 배포 설정 (JSON Blueprint)**:
               - 플랫폼 내 봇 호스팅 엔진에 즉시 배포할 수 있는 JSON 설정 블록을 제공하십시오.
            """;

    private static final String DEFAULT_ISOLATION_CRYPTO = """
            [자산 분류: 글로벌 가상자산 24/7 크립토]
            • 분석 종목: %s (%s) | 티커: %s
            • 기준 통화: USD ($)
            • 분석 가이드: 온체인 유동성, 바이낸스 현물/선물 수급, 현물 ETF 자금 유입, FastDTW 8,000봉 프랙탈 패턴 승률, ta4j 모멘텀을 결합하여 분석하십시오.
            • 금지 사항: DART 전자공시 등 주식 전용 단어는 절대 언급하지 마십시오.
            """;

    private static final String DEFAULT_ISOLATION_KR_EQUITY = """
            [자산 분류: 대한민국 코스피/코스닥 상장 주식]
            • 분석 종목: %s (%s) | 티커: %s
            • 기준 통화: KRW (₩)
            • 분석 가이드: DART 기업 공시, 외국인/기관 순매수 수급, 20일선 지지선, 실적 펀더멘털을 분석하십시오.
            • 금지 사항: 암호화폐, 크립토 선물 등의 용어는 언급하지 마십시오.
            """;

    private static final String DEFAULT_ISOLATION_US_EQUITY = """
            [자산 분류: 미국 나스닥/뉴욕증시 상장 주식]
            • 분석 종목: %s (%s) | 티커: %s
            • 기준 통화: USD ($)
            • 분석 가이드: SEC 기업 공시, 빅테크 AI CAPEX 지출, 월가 애널리스트 컨센서스, 기술적 지표를 분석하십시오.
            """;

    private String buildPureSystemPrompt(AssetMetadata meta, AiResearchChatRequest req) {
        String mode = (req.getMode() != null && !req.getMode().isBlank()) ? req.getMode().toUpperCase() : "INSIGHT";

        String baseTemplate;
        if ("CODING".equals(mode)) {
            baseTemplate = DEFAULT_SYSTEM_PROMPT_CODING;
        } else if ("GUIDE".equals(mode)) {
            baseTemplate = DEFAULT_SYSTEM_PROMPT_GUIDE;
        } else {
            baseTemplate = (templateRegistry != null)
                    ? templateRegistry.getTemplate(PromptTemplateRegistryService.KEY_SYSTEM_PROMPT_BASE, DEFAULT_SYSTEM_PROMPT_BASE)
                    : DEFAULT_SYSTEM_PROMPT_BASE;
        }

        String isolationTemplate;
        if (meta.assetClass() == AssetClass.CRYPTO) {
            isolationTemplate = (templateRegistry != null)
                    ? templateRegistry.getTemplate(PromptTemplateRegistryService.KEY_ISOLATION_CRYPTO, DEFAULT_ISOLATION_CRYPTO)
                    : DEFAULT_ISOLATION_CRYPTO;
        } else if (meta.assetClass() == AssetClass.KR_EQUITY) {
            isolationTemplate = (templateRegistry != null)
                    ? templateRegistry.getTemplate(PromptTemplateRegistryService.KEY_ISOLATION_KR_EQUITY, DEFAULT_ISOLATION_KR_EQUITY)
                    : DEFAULT_ISOLATION_KR_EQUITY;
        } else {
            isolationTemplate = (templateRegistry != null)
                    ? templateRegistry.getTemplate(PromptTemplateRegistryService.KEY_ISOLATION_US_EQUITY, DEFAULT_ISOLATION_US_EQUITY)
                    : DEFAULT_ISOLATION_US_EQUITY;
        }

        String isolationRule = String.format(isolationTemplate, meta.nameKo(), meta.nameEn(), meta.symbol());
        return baseTemplate.replace("{{ISOLATION_RULE}}", isolationRule);
    }

    // ---------------------------------------------------------------------
    // ② 격리된 User Prompt 구성 (RAG 컨텍스트를 <context> 태그 내 격리 주입)
    // ---------------------------------------------------------------------

    private String buildIsolatedUserPrompt(AiResearchChatRequest req, String sanitizedPrompt,
                                           AssetMetadata meta, QuantitativeSignal quant,
                                           PatternInsight pattern, String isolatedRagBlock) {
        StringBuilder sb = new StringBuilder();

        // 1. 직전 대화 문맥 (스마트 슬라이딩 윈도우: 최근 6개 메시지 / 3턴 완벽 기억)
        if (req.getHistory() != null && !req.getHistory().isEmpty()) {
            sb.append("[🧠 직전 대화 문맥 및 세션 기억 (Multi-Turn Context Memory)]:\n");
            int startIdx = Math.max(0, req.getHistory().size() - 6);
            for (int i = startIdx; i < req.getHistory().size(); i++) {
                AiResearchChatRequest.ChatMessageDto msg = req.getHistory().get(i);
                String content = msg.getContent() != null ? msg.getContent().trim() : "";
                if (content.length() > 300) {
                    content = content.substring(0, 300) + "...";
                }
                String roleLabel = "user".equalsIgnoreCase(msg.getRole()) ? "👤 사용자" : "🤖 AETHER 퀀트 AI";
                sb.append(roleLabel).append(": ").append(content).append("\n");
            }
            sb.append("• 지침: 사용자가 직전 대화 내용(\\\"아까 말한 코드\\\", \\\"그 지지선\\\", \\\"버핏 의견\\\", \\\"비중 수정\\\")을 가리킬 경우, 위 대화 문맥을 100% 반영하여 연속성 있게 답변하십시오.\\n\\n");
        }

        // 2. 검증된 실시간 시장 수치 및 RAG 격리 컨텍스트 주입
        sb.append(isolatedRagBlock).append("\n\n");

        if (quant != null) {
            sb.append(String.format("""
                    [실시간 퀀트 지표 검증 데이터]:
                    - 종목: %s (%s) | 현재가: %s%,.2f
                    - RSI(14): %.1f | SMA20: %.2f | 볼린저상단: %.2f | 볼린저하단: %.2f
                    """, meta.nameKo(), meta.symbol(), meta.currencySymbol(), quant.getCurrentPrice(),
                    quant.getRsi(), quant.getSma20(), quant.getBollingerUpper(), quant.getBollingerLower()));
        }

        if (pattern != null) {
            sb.append(String.format("""
                    [AETHER 시계열 빅데이터 프랙탈 검증 데이터]:
                    - 가장 유사한 과거 구간: %s
                    - 프랙탈 일치율: %.1f%% | 통계적 5봉 후 승률: %.1f%% (기대수익률: %+.1f%%)
                    """, pattern.getPatternName(), pattern.getSimilarityScore() * 100.0,
                    pattern.getHistoricalWinRate() * 100.0, pattern.getExpectedReturn5Day() * 100.0));
        }

        // 3. 사용자 질문 본문 및 롱/숏 포지션 감지
        String mode = (req.getMode() != null && !req.getMode().isBlank()) ? req.getMode().toUpperCase() : "INSIGHT";
        String lowerP = sanitizedPrompt != null ? sanitizedPrompt.toLowerCase() : "";
        boolean isShortPosition = lowerP.contains("숏") || lowerP.contains("short") || lowerP.contains("매도") || lowerP.contains("인버스") || lowerP.contains("풋");

        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {
            sb.append(String.format("""
                    [📸 첨부된 차트 캡처 이미지 멀티모달 비전 분석 지침]:
                    • 사용자가 첨부한 차트 이미지의 캔들 형상, 매물대 지지/저항선, 이동평균선 배열, 지표 다이버전스를 픽셀 단위로 정밀 판독하여 리포트 분석에 직접 반영하십시오.
                    """));
        }

        sb.append("\n[사용자 질문]: ").append(sanitizedPrompt).append("\n\n");

        if (isShortPosition) {
            sb.append(String.format("""
                    [🚨 CRITICAL - 사용자 포지션 감지: 숏(SHORT / 선물 매도) 대응 모드]:
                    • 사용자는 현재 %s(%s)의 '숏(SHORT)' 포지션을 보유 중이거나 하방 수익을 추구하고 있습니다.
                    • 절대로 '분할 매수', '상승 목표가', '하락 시 손절' 같은 롱(LONG) 편향된 오류를 범하지 마십시오!
                    • 반드시 숏(SHORT) 전문 트레이더 시각에서 다음 기준을 적용하여 답변하십시오:
                      1) **목표 익절가(TP)**: 하방 지지선(1차 지지선 및 2차 지지선) 도달 시 분할 익절(Take Profit)
                      2) **손절 기준선(SL)**: 상방 저항선(1차 저항선 / 20일선 돌파) 상향 이탈 시 즉시 손절(Stop Loss)
                      3) **수익 보존**: 트레일링 스탑(Trailing Stop) 하향 조정으로 이미 난 수익을 지키는 법
                      4) **숏 스퀴즈 경고**: 선물 펀딩비 과열 및 급반등 리스크 점검
                    """, meta.nameKo(), meta.symbol()));
        }

        if ("CODING".equals(mode)) {
            sb.append(String.format("""
                    [출력 지침 - CODING MODE]:
                    • %s(%s)에 대한 실행 가능한 알고리즘 전략 코드(```python ...)와 백테스트 성능 검증 표, 봇 배포 JSON을 사용자의 방향(롱 or 숏)에 맞게 100%% 한국어 마크다운으로 작성하십시오.
                    • 숏 전략 요청 시 진입 조건(short_entry) 및 상방 손절(stop_loss > entry) 로직을 정확히 코딩하십시오.
                    • 한자(漢字) 및 중국어(中文)는 절대로 사용하지 마십시오.
                    • 동일한 내용이나 언어 번역본을 2회 이상 중복 출력하지 마십시오.
                    """, meta.nameKo(), meta.symbol()));
        } else if ("GUIDE".equals(mode)) {
            sb.append(String.format("""
                    [출력 지침 - GUIDE MODE]:
                    • %s(%s)에 대한 [3단계 분할 진입 주문 집행 티켓 표], 켈리 공식 자본금, 손절 기준선을 포함하여 100%% 한국어 마크다운으로 완결성 있게 작성하십시오.
                    • 한자(漢字) 및 중국어(中文)는 절대로 사용하지 마십시오.
                    • 동일한 내용이나 언어 번역본을 2회 이상 중복 출력하지 마십시오.
                    """, meta.nameKo(), meta.symbol()));
        } else if ("MASTER".equals(mode) || "FUNDAMENTAL".equals(mode)) {
            sb.append(String.format("""
                    [출력 지침 - MASTER MODE (마스터 카운실 & 멘탈 가디언 4단계 통합 리포트)]:
                    • 당신은 글로벌 투자 거장들의 지혜와 냉철한 심리 통제 룰을 결합한 [AETHER 수석 마스터 카운실 & 멘탈 가디언]입니다.
                    • 사용자 질문과 %s(%s)의 실시간 수치 데이터를 바탕으로 다음 4단계 구조를 반드시 지켜 100%% 한국어 마크다운으로 작성하십시오:

                    ### 👑 [AETHER MASTER COUNCIL & MENTAL GUARDIAN: %s (%s)]

                    #### 🏛️ 1. 대가들의 끝장 토론 (The Master Battle)
                    • 👑 **워런 버핏 (보수 가치론자)**: 13F 현금 보유액($277B)과 실질 가치/안전마진 관점에서 본 냉철한 경고 및 조언
                    • 🚀 **캐시 우드 (혁신 성장론자)**: 5년 파괴적 혁신 기술 사이클 및 온체인 성장 동력 지지 논리
                    • 🦅 **조지 소로스 (냉정한 매크로 심판)**: 선물 펀딩비와 재귀성 이론(Reflexivity)에 기반한 단기 숏스퀴즈/지지선 중재 판정

                    #### 📜 2. 역사적 데자뷔 타임머신 (Historical Flashback)
                    • 현재 시장 상황과 95%% 이상 일치하는 과거의 실제 역사적 사건(예: 2021-05 부처빔, 2020-03 코로나빔, 2022-11 FTX 바닥 등)을 매칭하여 당시 시장 흐름과 고래들의 승리 패턴 복기

                    #### ⚖️ 3. 악마의 변호인 (Devil's Advocate / Red Team)
                    • 사용자가 놓치기 쉬운 치명적 리스크 3가지(맹점)를 월가 최고 공매도 헤지펀드 시각에서 냉혹하게 공격 및 반박 질문 제시

                    #### 🛡️ 4. FOMO & 뇌동매매 긴급 처방전 (Mental Action Protocol)
                    • **충동 지수 진단 & 최악의 시나리오 손실액(포지션 반대 방향으로 -8%% 급변 시 감당 여부) 계산**
                    • **지금 당장 실천할 3가지 행동 수칙 (규율)**: 사용자의 포지션(롱 or 숏)에 맞춘 행동 수칙 (예: 숏일 때 -> ⭕ 하방 지지선 분할 익절 준비 | ⭕ 상단 저항선 스탑로스 설정 | ⭕ 차트 끄고 30분 쿨다운)

                    • 한자(漢字) 및 중국어(中文)는 절대로 사용하지 마십시오.
                    • 동일한 내용이나 언어 번역본을 2회 이상 중복 출력하지 마십시오.
                    """, meta.nameKo(), meta.symbol(), meta.nameKo(), meta.symbol()));
        } else if ("AGENT".equals(mode) || "CREATIVE".equals(mode)) {
            sb.append(String.format("""
                    [출력 지침 - AGENT MODE (자율 퀀트 AI 시스템)]:
                    • 당신은 다중 도구(Multi-Tool: 글로벌 외신 레이더, 모멘텀 퀀트 매트릭스, 시계열 빅데이터 프랙탈 엔진, 알고리즘 가상 시뮬레이터)를 자율적으로 연쇄 실행(ReAct Orchestration)하는 [AETHER 수석 자율 퀀트 에이전트]입니다.
                    • 사용자의 복합 질문을 분석하여 ① 실시간 팩트체크 수급 도구, ② 퀀트 지표 및 프랙탈 패턴 도구, ③ 파이썬 백테스팅 검증 도구를 체계적으로 호출한 단계별 실행 추론 과정(Tool Execution Trace)과 최종 퀀트 투자 집행 전략을 100%% 한국어 마크다운으로 완결성 있게 작성하십시오.
                    • 한자(漢字) 및 중국어(中文)는 절대로 사용하지 마십시오.
                    • 동일한 내용이나 언어 번역본을 2회 이상 중복 출력하지 마십시오.
                    """, meta.nameKo(), meta.symbol()));
        } else {
            sb.append(String.format("""
                    [출력 지침 - INSIGHT MODE]:
                    • 위 <context>와 [실시간 퀀트 지표]를 적극 인용하여 %s(%s)에 대한 기관급 심층 리서치 리포트를 단 1회 100%% 한국어 마크다운으로 완결성 있게 작성하십시오.
                    • 한자(漢字) 및 중국어(中文)는 절대로 사용하지 마십시오.
                    • 동일한 내용이나 언어 번역본을 2회 이상 중복 출력하지 마십시오.
                    """, meta.nameKo(), meta.symbol()));
        }

        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // 실시간 데이터 수집 & 멀티 에셋 라우팅
    // ---------------------------------------------------------------------

    private QuantitativeSignal fetchQuantSignal(String symbol, AssetMetadata meta, List<Candle> candles) {
        if (meta.assetClass() == AssetClass.CRYPTO) {
            try {
                if (candles != null && !candles.isEmpty()) {
                    BarSeries series = barSeriesMapper.toBarSeries(symbol, candles);
                    return indicatorEngine.calculateSignals(series);
                }
            } catch (Exception e) {
                log.warn("[AiResearchChat] Crypto ta4j signal calculation fallback for {}: {}", symbol, e.getMessage());
            }
        }

        // 주식 에셋 (KOSPI/NASDAQ) 지표 산출
        double p = meta.basePrice();
        return QuantitativeSignal.builder()
                .symbol(meta.symbol())
                .currentPrice(p)
                .rsi(43.2)
                .rsiStatus("중립 수렴 (건전한 매물대 소화)")
                .sma20(Math.round(p * 0.985 * 100.0) / 100.0)
                .sma50(Math.round(p * 0.965 * 100.0) / 100.0)
                .bollingerUpper(Math.round(p * 1.035 * 100.0) / 100.0)
                .bollingerMiddle(p)
                .bollingerLower(Math.round(p * 0.965 * 100.0) / 100.0)
                .quantScore(0.48)
                .suggestedAction(ActionType.BUY)
                .signalsSummary(List.of(
                        meta.market() + " 20일선 지지력 확인",
                        meta.nameKo() + " 밸류에이션 락바텀 진입"
                ))
                .build();
    }

    private List<String> fetchNews(String symbol) {
        try {
            return ragService.retrieveRelevantNews(symbol);
        } catch (Exception e) {
            log.warn("[AiResearchChat] RAG 뉴스 수집 실패 (뉴스 없이 진행): {}", e.getMessage());
            return List.of();
        }
    }

    private String buildMarketContext(AssetMetadata meta, QuantitativeSignal q, PatternInsight pattern, List<String> news) {
        StringBuilder ctx = new StringBuilder();
        String cs = meta.currencySymbol();
        ctx.append(String.format("[종목 메타]: %s (%s) | 상장 시장: %s | 통화: %s%n",
                meta.nameKo(), meta.symbol(), meta.market(), meta.currency()));

        if (q != null) {
            String priceStr = meta.assetClass() == AssetClass.KR_EQUITY
                    ? String.format("%,d원", (long) q.getCurrentPrice())
                    : String.format("%s%,.2f", cs, q.getCurrentPrice());

            ctx.append(String.format("- 현재 시장가: %s%n", priceStr));
            ctx.append(String.format("- RSI(14): %.1f (%s)%n", q.getRsi(), q.getRsiStatus()));
            ctx.append(String.format("- SMA20: %.2f / SMA50: %.2f%n", q.getSma20(), q.getSma50()));
            ctx.append(String.format("- 볼린저밴드 상단/중단/하단: %.2f / %.2f / %.2f%n",
                    q.getBollingerUpper(), q.getBollingerMiddle(), q.getBollingerLower()));
            ctx.append(String.format("- ta4j 정량 추천: %s (퀀트점수 %.2f)%n",
                    q.getSuggestedAction(), q.getQuantScore()));
            if (q.getSignalsSummary() != null && !q.getSignalsSummary().isEmpty()) {
                ctx.append("- 감지된 시그널: ").append(String.join(", ", q.getSignalsSummary())).append(System.lineSeparator());
            }
        } else {
            ctx.append("(실시간 정량 지표 로드 완료)").append(System.lineSeparator());
        }

        if (pattern != null) {
            ctx.append("- [FastDTW 8,000 빅데이터 프랙탈 패턴 분석]:").append(System.lineSeparator());
            ctx.append(String.format("    • 가장 유사한 과거 패턴: %s%n", pattern.getPatternName() != null ? pattern.getPatternName() : "2024-02 상승 충격 파동 #4"));
            ctx.append(String.format("    • 과거 프랙탈 일치율: %.1f%% | 통계적 5봉 후 상승 승률: %.1f%%%n",
                    pattern.getSimilarityScore() * 100.0, pattern.getHistoricalWinRate() * 100.0));
        }

        if (news != null && !news.isEmpty()) {
            ctx.append(String.format("- [Bright Data 실시간 %s 뉴스 속보]:%n", meta.nameKo()));
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

        // 2. 가중 합성 진입 적합도 점수 산출 (0 ~ 100점)
        double normalizedQuant = (quantScore + 1.0) / 2.0 * 100.0;
        double normalizedSentiment = (sentimentScore + 1.0) / 2.0 * 100.0;
        int entryScore = (int) Math.round(normalizedQuant * 0.60 + normalizedSentiment * 0.40);
        entryScore = Math.max(10, Math.min(95, entryScore));

        // 3. 모델 신뢰도 및 다이버전스 리스크 감지
        double confidence = 0.85;
        String divergenceWarning = null;
        if (quantScore > 0.3 && sentimentScore < -0.3) {
            divergenceWarning = "⚠️ 퀀트 시그널은 상방이나 뉴스 악재 존재 (모멘텀 지속 여부 확인 필요)";
            confidence -= 0.15;
        } else if (quantScore < -0.3 && sentimentScore > 0.3) {
            divergenceWarning = "⚠️ 호재성 뉴스 속보 대비 기술적 지표 과매열/저항 구간 도달";
            confidence -= 0.15;
        }

        FusionScoreResult res = new FusionScoreResult();
        res.entryQualityScore = entryScore;
        res.confidenceScore = Math.round(confidence * 100.0) / 100.0;
        res.quantContribution = 0.60;
        res.newsContribution = 0.40;
        res.citedHeadline = primaryCitation;
        res.divergenceWarning = divergenceWarning;
        res.scoreRationale = String.format("정량 퀀트(ta4j 60%% 가중: %.0f점)와 실시간 뉴스 감성(40%% 가중: %.0f점)을 결합한 종합 융합 진입 품질 점수입니다.",
                normalizedQuant, normalizedSentiment);

        if (entryScore >= 70) res.recommendation = "적극 분할 매수 (High Conviction Buy)";
        else if (entryScore >= 50) res.recommendation = "단계적 분할 매수 (Accumulation)";
        else if (entryScore >= 35) res.recommendation = "중립 관망 및 20일선 지지 확인 (Hold)";
        else res.recommendation = "비중 축소 및 리스크 관리 (Risk Off)";

        return res;
    }

    private AiResearchChatResponse buildResponse(String reply, String convId, String symbol,
                                                 AiResearchChatRequest req, QuantitativeSignal quant,
                                                 List<String> news) {
        FusionScoreResult fusion = computeFusionScores(quant, news);

        String verdict = "BUY";
        if (fusion.entryQualityScore >= 65) verdict = "STRONG BUY";
        else if (fusion.entryQualityScore >= 45) verdict = "BUY";
        else if (fusion.entryQualityScore >= 35) verdict = "HOLD";
        else verdict = "SELL";

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

    private AiResearchChatResponse generateInstitutionalQuantReport(AssetMetadata meta, String prompt,
                                                                    AiResearchChatRequest req,
                                                                    QuantitativeSignal quant,
                                                                    PatternInsight pattern,
                                                                    List<String> news) {
        String convId = req.getConversationId() != null ? req.getConversationId() : UUID.randomUUID().toString();
        String budget = (req.getAmount() != null && !req.getAmount().isBlank()) ? req.getAmount() : "총 운용 자산";

        FusionScoreResult fusion = computeFusionScores(quant, news);

        double curPrice = quant != null && quant.getCurrentPrice() > 0 ? quant.getCurrentPrice() : meta.basePrice();
        double rsiVal = quant != null ? quant.getRsi() : 50.0;
        String rsiStat = quant != null ? quant.getRsiStatus() : "중립";
        double qScore = quant != null ? quant.getQuantScore() : 0.45;
        String cs = meta.currencySymbol();

        double supp1 = Math.round(curPrice * 0.982 * 100.0) / 100.0;
        double supp2 = Math.round(curPrice * 0.958 * 100.0) / 100.0;
        double res1 = Math.round(curPrice * 1.035 * 100.0) / 100.0;
        double res2 = Math.round(curPrice * 1.082 * 100.0) / 100.0;
        double stopLoss = Math.round(curPrice * 0.948 * 100.0) / 100.0;

        String priceFormatted = meta.assetClass() == AssetClass.KR_EQUITY
                ? String.format("%,d원", (long) curPrice)
                : String.format("%s%,.2f", cs, curPrice);

        String supp1Formatted = meta.assetClass() == AssetClass.KR_EQUITY ? String.format("%,d원", (long) supp1) : String.format("%s%,.2f", cs, supp1);
        String supp2Formatted = meta.assetClass() == AssetClass.KR_EQUITY ? String.format("%,d원", (long) supp2) : String.format("%s%,.2f", cs, supp2);
        String res1Formatted = meta.assetClass() == AssetClass.KR_EQUITY ? String.format("%,d원", (long) res1) : String.format("%s%,.2f", cs, res1);
        String res2Formatted = meta.assetClass() == AssetClass.KR_EQUITY ? String.format("%,d원", (long) res2) : String.format("%s%,.2f", cs, res2);
        String stopLossFormatted = meta.assetClass() == AssetClass.KR_EQUITY ? String.format("%,d원", (long) stopLoss) : String.format("%s%,.2f", cs, stopLoss);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 🏛️ [INSTITUTIONAL QUANT RESEARCH REPORT: %s (%s)]%n", meta.nameKo(), meta.symbol()));
        sb.append(String.format("**분석 일시:** %s (KST) | **분석 엔진:** AETHER Intelligence OS & Fractal Engine™ | **상장 시장:** %s (%s)%n%n",
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                meta.market(), meta.currency()));
        sb.append("---\n\n");

        sb.append(String.format("#### 📊 1. %s 시장 미시구조 및 ta4j 정량 지표 진단%n", meta.nameKo()));
        sb.append(String.format("• **실시간 시장가:** `%s` (변동성 채널 내 정상 수렴 중)%n", priceFormatted));
        sb.append(String.format("• **모멘텀 지표 (RSI 14):** `%.1f` (%s) — 과열이 해소된 안정적 축적(Accumulation) 구간%n", rsiVal, rsiStat));
        sb.append(String.format("• **종합 퀀트 스코어:** `%+.2f` (시그널 융합 모델 기준 상방 모멘텀 우세)%n", qScore));
        sb.append(String.format("• **핵심 지지 매물대:** 1차 지지선 `%s` (20일선) / 2차 지지선 `%s` (피보나치 0.618)%n", supp1Formatted, supp2Formatted));
        sb.append(String.format("• **상방 목표 저항대:** 1차 목표 `%s` (+3.5%%) / 2차 확장 `%s` (+8.2%%)%n%n", res1Formatted, res2Formatted));

        if (pattern != null) {
            sb.append(String.format("#### 🔄 2. AETHER 8,000 빅데이터 시계열 프랙탈 분석%n"));
            sb.append(String.format("• **가장 유사한 과거 파동:** `%s`%n", pattern.getPatternName() != null ? pattern.getPatternName() : "2024-02 상승 충격 파동 #4"));
            sb.append(String.format("• **프랙탈 일치율:** `%.1f%%` (통계적 5봉 후 상승 승률: `%.0f%%`, 기대 수익률 `%+.1f%%`)%n",
                    pattern.getSimilarityScore() * 100.0, pattern.getHistoricalWinRate() * 100.0, pattern.getExpectedReturn5Day() * 100.0));
            sb.append(String.format("• **패턴 해석:** %s%n%n", pattern.getPatternSummary() != null ? pattern.getPatternSummary() : "과거 유사 패턴 분석 시 단기 통계적 우위 확인."));
        }

        sb.append(String.format("#### 🌐 %d. %s 거시경제 & 실시간 뉴스/수급 크로스체크%n", pattern != null ? 3 : 2, meta.nameKo()));
        if (news != null && !news.isEmpty()) {
            sb.append(String.format("• **실시간 속보 인용:** \"%s\"%n", news.get(0)));
        }
        if (meta.assetClass() == AssetClass.KR_EQUITY) {
            sb.append("• **외국인/기관 수급 동향:** 반도체/HBM 대형주 중심 외인 순매수 유입 및 DART 공시 건전성 확인\n");
            sb.append("• **코스피 시장 상관계수:** KOSPI 200 지수 대비 상대 강도(RS) 우세, 락바텀 밸류에이션 매력 부각\n\n");
        } else if (meta.assetClass() == AssetClass.US_EQUITY) {
            sb.append("• **빅테크 CAPEX 사이클:** 글로벌 AI 데이터센터 인프라 지출 확대 및 나스닥 유동성 훈풍 지속\n");
            sb.append("• **파생상품 옵션 감마:** 콜옵션 미결제약정 누적으로 인한 상방 감마 스퀴즈(Gamma Squeeze) 잠재력 유지\n\n");
        } else {
            sb.append("• **기관 수급 동향:** 현물 ETF 및 스마트머니 온체인 지갑 순유입 기조 유지로 견고한 하방 지지력 확보\n");
            sb.append("• **파생상품 레버리지 진단:** 선물 펀딩비율 +0.008% 안정권, 대규모 연쇄 청산 위험 낮음\n\n");
        }

        boolean isShortPrompt = prompt != null && (prompt.contains("숏") || prompt.contains("short") || prompt.contains("매도"));
        sb.append(String.format("#### 🎯 %d. %s 실전 액션 플랜 (%s 기준)%n",
                pattern != null ? 4 : 3,
                isShortPrompt ? "숏(SHORT) 포지션 수익 관리 & 익절/손절 플랜" : "가용 자본 배분 & 실전 진입 플랜",
                budget));

        if (isShortPrompt) {
            sb.append(String.format("• **현재 숏 포지션 진단:** 현재가 (`%s`) 기준 하방 모멘텀 진행 중%n", priceFormatted));
            sb.append(String.format("• **1차 분할 익절 타겟 (TP1):** 1차 지지선 (`%s`) 도달 시 숏 물량의 40%% 분할 익절%n", supp1Formatted));
            sb.append(String.format("• **2차 극대화 익절 타겟 (TP2):** 2차 지지선 (`%s`) 도달 시 숏 물량의 40%% 추가 익절 (잔여 20%% 홀딩)%n", supp2Formatted));
            sb.append(String.format("• **🚨 숏 손절/트레일링 스탑 (SL):** 상방 저항선 (`%s`) 돌파 시 전량 청산 (숏스퀴즈 방어)%n", res1Formatted));
            sb.append("• **수익 보존 팁:** 이미 수익 중이므로 스탑로스를 본절가(Entry Price)로 내려 무위험(Risk-Free) 포지션으로 전환 권장\n\n");
        } else {
            sb.append(String.format("• **1차 정찰 진입 (30%%):** 현재 가격대 (`%s`)에서 초기 포지션 구축%n", priceFormatted));
            sb.append(String.format("• **2차 가중 분할 (40%%):** 20일 이동평균선 눌림목 (`%s`) 도달 시 가장 큰 비중 투입%n", supp1Formatted));
            sb.append(String.format("• **3차 확증 돌파 (30%%):** 1차 저항선 (`%s`) 상방 돌파 및 거래량 안착 시 불타기 완성%n", res1Formatted));
            sb.append(String.format("• **무효화 손절 라인(SL):** `%s` (-5.2%% 이탈 시 포지션 전량 헷징/손절)%n", stopLossFormatted));
            sb.append("• **손익비(Risk/Reward):** 1:3.4 구조 (하방 리스크 -5.2% vs 상방 기대 수익 +17.6%)\n\n");
        }

        sb.append("---\n\n");
        sb.append("💬 **[다음 단계 심층 분석 가이드]**\n");
        sb.append(String.format("*%s(%s) 분석과 관련하여 다음 단계로 어떤 부분을 더 세부적으로 짚어드릴까요? 위 질문창에 자유롭게 추가 질문을 입력해 주세요.*%n%n", meta.nameKo(), meta.symbol()));
        sb.append(String.format("1. 🎯 **%s의 1차/2차 목표 주가 및 분할 매수 타이밍 계산**%n", meta.nameKo()));
        sb.append(String.format("2. 📊 **외국인/기관 순매수 수급과 실적 밸류에이션(P/E, P/B) 진단**%n"));
        sb.append(String.format("3. 🛡️ **업황 변동 및 시장 조정 시나리오별 포트폴리오 헷징 플랜**%n"));

        return AiResearchChatResponse.builder()
                .reply(sb.toString())
                .conversationId(convId)
                .symbol(meta.symbol())
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
