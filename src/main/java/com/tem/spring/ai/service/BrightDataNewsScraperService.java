package com.tem.spring.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Bright Data Web Intelligence & Scraping API 연동 서비스
 * 종목별 실시간 금융 뉴스, 실적 속보 및 공시 데이터 실시간 수집
 */
@Slf4j
@Service
public class BrightDataNewsScraperService {

    @Value("${brightdata.api-key:4a62ad76-a8e4-46cb-9cb0-deaf9e6587a7}")
    private String apiKey;

    @Value("${brightdata.base-url:https://api.brightdata.com}")
    private String baseUrl;

    @Value("${brightdata.enabled:true}")
    private boolean enabled;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BrightDataNewsScraperService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 특정 종목(주식/코인)의 실시간 최신 뉴스 및 공시 스크래핑 수집
     */
    public List<String> scrapeRealtimeFinancialNews(String symbol) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.debug("[BrightData] API is disabled or key not provided");
            return List.of();
        }

        String searchKeyword = resolveSearchKeyword(symbol);
        log.info("[BrightData] Initiating real-time web scraping for: {} ({})", symbol, searchKeyword);

        try {
            // Bright Data SERP / Scraping Request
            String response = webClient.post()
                    .uri(baseUrl + "/serp/req")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .bodyValue(String.format("{\"query\": \"%s 최근 주가 실적 공시 뉴스\", \"num\": 3, \"lang\": \"ko\"}", searchKeyword))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(4))
                    .block();

            if (response != null && !response.isBlank()) {
                JsonNode root = objectMapper.readTree(response);
                List<String> headlines = new ArrayList<>();
                JsonNode organic = root.path("organic");
                if (organic.isArray()) {
                    for (JsonNode item : organic) {
                        String title = item.path("title").asText("");
                        String snippet = item.path("snippet").asText("");
                        if (!title.isBlank()) {
                            headlines.add(String.format("[속보/BrightData] %s - %s", title, snippet));
                        }
                    }
                }
                if (!headlines.isEmpty()) {
                    log.info("[BrightData] Successfully scraped {} news items for {}", headlines.size(), symbol);
                    return headlines;
                }
            }
        } catch (Exception e) {
            log.warn("[BrightData] Web Scraping request completed with status/fallback for {}: {}", symbol, e.getMessage());
        }

        return List.of();
    }

    private String resolveSearchKeyword(String symbol) {
        String upper = symbol.toUpperCase().trim();
        return switch (upper) {
            case "005930", "SAMSUNG" -> "삼성전자 005930";
            case "000660", "SKHYNIX", "SK_HYNIX" -> "SK하이닉스 000660";
            case "NVDA", "NVIDIA" -> "엔비디아 NVDA";
            case "AAPL", "APPLE" -> "애플 AAPL";
            case "TSLA", "TESLA" -> "테슬라 TSLA";
            case "BTCUSDT", "BTC" -> "비트코인 BTC";
            case "ETHUSDT", "ETH" -> "이더리움 ETH";
            case "SOLUSDT", "SOL" -> "솔라나 SOL";
            default -> symbol;
        };
    }
}
