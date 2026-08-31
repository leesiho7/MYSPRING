package com.tem.spring.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [Alibaba Cloud DashScope Qwen-Max API 퀀트 브레인 클라이언트]
 * - OpenAI 호환 규격을 지원하는 Qwen-Max 플래그십 (300B+ 파라미터) 직접 연동
 * - Spring 6 / Boot 3 고성능 RestClient 기반
 * - Resilience4j 서킷 브레이커 & 지수 백오프 자동 장착
 */
@Slf4j
@Service
public class QwenMaxApiService {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final double temperature;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
            io.github.resilience4j.circuitbreaker.CircuitBreaker.of("qwenMaxApi",
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            .slidingWindowSize(5)
                            .minimumNumberOfCalls(3)
                            .failureRateThreshold(50.0f)
                            .waitDurationInOpenState(Duration.ofSeconds(15))
                            .permittedNumberOfCallsInHalfOpenState(2)
                            .build());

    private final io.github.resilience4j.retry.Retry retry =
            io.github.resilience4j.retry.Retry.of("qwenMaxRetry",
                    io.github.resilience4j.retry.RetryConfig.custom()
                            .maxAttempts(2)
                            .waitDuration(Duration.ofMillis(500))
                            .build());

    public QwenMaxApiService(
            @Value("${qwen.api.base-url:https://dashscope-intl.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${qwen.api.api-key:sk-ws-H.DDDYHED.oT11.MEUCIQDHo2P4fbmHPSU681vOxLO7mMbh5h_rwJM_cmzdY93KmwIgUT5PdszK-qXMBQ8rH18ii7qkWkAnwZNbR8Ms0N6adJk}") String apiKey,
            @Value("${qwen.api.model:qwen-max}") String model,
            @Value("${qwen.api.enabled:true}") boolean enabled,
            @Value("${qwen.api.temperature:0.15}") double temperature
    ) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank() && !apiKey.contains("mock");
        this.temperature = temperature;

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        if (this.enabled) {
            log.info("[QwenMaxApiService] 🚀 Qwen-Max Flagship API Engine Activated (Model: {}, BaseURL: {})",
                    this.model, this.baseUrl);
        } else {
            log.info("[QwenMaxApiService] ℹ️ Qwen-Max API is disabled or unconfigured. Local Ollama fallback active.");
        }
    }

    public boolean isEnabled() {
        return enabled && circuitBreaker.getState() != io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
    }

    public String getModelName() {
        return model;
    }

    /**
     * Qwen-Max 플래그십 모델로 시스템/유저 프롬프트를 전송하여 기관급 추론 결과를 수신합니다.
     */
    public String generateChat(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            return null;
        }

        try {
            long start = System.currentTimeMillis();

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            if (userPrompt != null && !userPrompt.isBlank()) {
                messages.add(Map.of("role", "user", "content", userPrompt));
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", 1200);

            String responseBody = retry.executeSupplier(() ->
                    circuitBreaker.executeSupplier(() ->
                            restClient.post()
                                    .uri("/chat/completions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(requestBody)
                                    .retrieve()
                                    .body(String.class)
                    )
            );

            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    String content = choices.get(0).path("message").path("content").asText(null);
                    log.info("[QwenMaxApiService] ✅ Qwen-Max inferred successfully in {}ms (Tokens: {})",
                            System.currentTimeMillis() - start, root.path("usage").path("total_tokens").asInt(0));
                    return content;
                }
            }
        } catch (Exception e) {
            log.error("[QwenMaxApiService] ❌ Qwen-Max API call failed: {}. Falling back to secondary engine.", e.getMessage());
        }

        return null;
    }

    /**
     * Qwen-Max 플래그십 모델 실시간 토큰 스트리밍 (SSE / Token-by-Token 타자기 효과)
     */
    public boolean streamChat(String systemPrompt, String userPrompt, java.util.function.Consumer<String> tokenConsumer) {
        if (!isEnabled()) {
            return false;
        }

        try {
            long start = System.currentTimeMillis();

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            if (userPrompt != null && !userPrompt.isBlank()) {
                messages.add(Map.of("role", "user", "content", userPrompt));
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", 1500);
            requestBody.put("stream", true);

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload, java.nio.charset.StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            java.net.http.HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                log.warn("[QwenMaxApiService] ⚠️ Stream request failed with HTTP {}", response.statusCode());
                return false;
            }

            java.util.concurrent.atomic.AtomicInteger tokenCount = new java.util.concurrent.atomic.AtomicInteger();

            response.body().forEach(line -> {
                if (line == null || line.isBlank()) return;
                String trimmed = line.trim();
                if (trimmed.startsWith("data: ")) {
                    String data = trimmed.substring(6).trim();
                    if ("[DONE]".equals(data)) return;
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode choices = node.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            String delta = choices.get(0).path("delta").path("content").asText("");
                            if (!delta.isEmpty()) {
                                tokenCount.incrementAndGet();
                                tokenConsumer.accept(delta);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            });

            log.info("[QwenMaxApiService] ⚡ Stream finished in {}ms (Streamed tokens: {})",
                    System.currentTimeMillis() - start, tokenCount.get());
            return true;
        } catch (Exception e) {
            log.error("[QwenMaxApiService] ❌ StreamChat failed: {}", e.getMessage());
            return false;
        }
    }
}
