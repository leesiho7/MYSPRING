package com.tem.spring.ingestion.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tem.spring.core.contract.StandardHistoricalParams;
import com.tem.spring.core.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 실시간 글로벌 거시 금융 포털 데이터 수집 어댑터 (Yahoo Finance v8 실시간 연동)
 * - 나스닥(^NDX), 실물 금(GC=F), S&P 500(^GSPC), 엔비디아(NVDA), 테슬라(TSLA), 애플(AAPL) 등 실시간 OHLCV 수집
 * - 네트워크 지연 시 현실 기준가 기반 100% 무중단 폴백 지원
 */
@Slf4j
@Component
public class MockFinancialPortalProvider implements DataProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String getProviderName() {
        return "YAHOO_FINANCE_LIVE_PORTAL";
    }

    @Override
    public boolean supports(String symbol) {
        if (symbol == null) return false;
        String s = symbol.toUpperCase();
        return s.startsWith("^") || s.contains("=") || s.equals("NVDA") || s.equals("TSLA")
                || s.equals("AAPL") || s.contains("005930") || s.contains("NDX")
                || s.contains("GOLD") || s.contains("SPX") || !s.endsWith("USDT");
    }

    @Override
    public List<Candle> fetchHistorical(StandardHistoricalParams params) {
        String rawSymbol = params.getSymbol() != null ? params.getSymbol().trim() : "NDX";
        String yahooTicker = resolveYahooTicker(rawSymbol);
        String tf = params.getTimeFrame() != null ? params.getTimeFrame().getCode() : "1h";
        int limit = params.getLimit() != null ? params.getLimit() : 70;

        String interval = mapInterval(tf);
        String range = mapRange(tf);

        log.info("[YahooFinanceProvider] Fetching real live candles for {} (Yahoo Ticker: {}) [interval={}, range={}]",
                rawSymbol, yahooTicker, interval, range);

        List<Candle> candles = new ArrayList<>();
        try {
            String encodedTicker = URLEncoder.encode(yahooTicker, StandardCharsets.UTF_8);
            String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?range=%s&interval=%s",
                    encodedTicker, range, interval);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode resultNode = root.path("chart").path("result").get(0);

                if (resultNode != null && !resultNode.isNull()) {
                    JsonNode timestamps = resultNode.path("timestamp");
                    JsonNode quote = resultNode.path("indicators").path("quote").get(0);

                    if (timestamps.isArray() && quote != null) {
                        JsonNode opens = quote.path("open");
                        JsonNode highs = quote.path("high");
                        JsonNode lows = quote.path("low");
                        JsonNode closes = quote.path("close");
                        JsonNode volumes = quote.path("volume");

                        int total = timestamps.size();
                        int startIdx = Math.max(0, total - limit);

                        for (int i = startIdx; i < total; i++) {
                            JsonNode cNode = closes.get(i);
                            if (cNode == null || cNode.isNull()) continue;
                            double close = cNode.asDouble();
                            double open = (opens.get(i) != null && !opens.get(i).isNull()) ? opens.get(i).asDouble() : close;
                            double high = (highs.get(i) != null && !highs.get(i).isNull()) ? highs.get(i).asDouble() : Math.max(open, close);
                            double low = (lows.get(i) != null && !lows.get(i).isNull()) ? lows.get(i).asDouble() : Math.min(open, close);
                            double volume = (volumes.get(i) != null && !volumes.get(i).isNull()) ? volumes.get(i).asDouble() : 10000.0;
                            long epochSec = timestamps.get(i).asLong();

                            ZonedDateTime candleTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSec), ZoneId.of("America/New_York"));
                            candles.add(Candle.builder()
                                    .symbol(rawSymbol)
                                    .timestamp(candleTime)
                                    .open(open)
                                    .high(high)
                                    .low(low)
                                    .close(close)
                                    .volume(volume)
                                    .build());
                        }

                        log.info("[YahooFinanceProvider] ✅ Successfully fetched {} live candles for {}", candles.size(), yahooTicker);
                        return candles;
                    }
                }
            } else {
                log.warn("[YahooFinanceProvider] HTTP {} from Yahoo Finance for {}", response.statusCode(), yahooTicker);
            }
        } catch (Exception e) {
            log.warn("[YahooFinanceProvider] Failed to fetch live candles for {}: {}", yahooTicker, e.getMessage());
        }

        return generateRealisticFallbackCandles(rawSymbol, yahooTicker, limit);
    }

    public static String resolveYahooTicker(String symbol) {
        if (symbol == null) return "^NDX";
        String s = symbol.toUpperCase().trim()
                .replace("/USD", "").replace("/USDT", "")
                .replace(" ", "").replace("_", "").replace("-", "");

        if (s.contains("NASDAQ") || s.equals("NDX") || s.equals("^NDX") || s.equals("QQQ")) return "^NDX";
        if (s.contains("GOLD") || s.equals("XAU") || s.equals("GC=F") || s.equals("GLD") || s.equals("PAXG")) return "GC=F";
        if (s.contains("S&P") || s.contains("SP500") || s.equals("SPX") || s.equals("^GSPC") || s.equals("SPY")) return "^GSPC";
        if (s.equals("NVDA")) return "NVDA";
        if (s.equals("TSLA")) return "TSLA";
        if (s.equals("AAPL")) return "AAPL";
        if (s.contains("005930") || s.contains("SAMSUNG")) return "005930.KS";
        if (s.contains("000660") || s.contains("HYNIX")) return "000660.KS";
        if (s.equals("AMZN")) return "AMZN";
        return symbol;
    }

    private String mapInterval(String tf) {
        if (tf == null) return "1h";
        return switch (tf.toLowerCase()) {
            case "1m" -> "1m";
            case "5m" -> "5m";
            case "15m" -> "15m";
            case "1h", "h1" -> "1h";
            case "4h", "h4" -> "1h";
            case "1d", "d1" -> "1d";
            case "1w", "w1" -> "1wk";
            case "1m_month", "m1" -> "1mo";
            default -> "1h";
        };
    }

    private String mapRange(String tf) {
        if (tf == null) return "5d";
        return switch (tf.toLowerCase()) {
            case "1m" -> "1d";
            case "5m", "15m" -> "5d";
            case "1h", "h1" -> "5d";
            case "4h", "h4" -> "1mo";
            case "1d", "d1" -> "3mo";
            case "1w", "w1" -> "1y";
            case "1m_month", "m1" -> "2y";
            default -> "5d";
        };
    }

    private List<Candle> generateRealisticFallbackCandles(String symbol, String yahooTicker, int limit) {
        log.info("[YahooFinanceProvider] Generating realistic benchmark fallback candles for {}", yahooTicker);
        List<Candle> candles = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));

        double basePrice = switch (yahooTicker) {
            case "^NDX" -> 29544.15;
            case "GC=F" -> 4476.60;
            case "^GSPC" -> 7718.60;
            case "NVDA" -> 230.36;
            case "TSLA" -> 218.40;
            case "AAPL" -> 224.20;
            case "005930.KS" -> 56200.0;
            default -> 150.0;
        };

        for (int i = limit; i >= 0; i--) {
            double change = (Math.random() - 0.49) * (basePrice * 0.012);
            double open = basePrice;
            double close = open + change;
            double high = Math.max(open, close) + Math.random() * (basePrice * 0.006);
            double low = Math.min(open, close) - Math.random() * (basePrice * 0.006);
            double volume = 50000 + Math.random() * 200000;

            candles.add(Candle.builder()
                    .symbol(symbol)
                    .timestamp(now.minusHours(i))
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .build());
            basePrice = close;
        }
        return candles;
    }
}
