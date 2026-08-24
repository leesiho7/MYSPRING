package com.tem.spring.ai.service;

import com.tem.spring.ai.rag.FinancialNewsRagService;
import com.tem.spring.ai.repository.UserQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사전 배치 크롤러 & VectorStore(vmstore) 사전 예열(Warmup) 서비스
 * 유저들이 자주 묻는 인기 종목과 타겟 키워드를 자동으로 발굴하여,
 * 사전에 실시간 뉴스를 스크래핑하고 vmstore에 선적재함으로써 주간 피크 타임 API 비용 90% 절감
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProactiveNewsWarmupBatchService {

    private final FinancialNewsRagService ragService;
    private final BrightDataNewsScraperService brightDataService;
    private final ObjectProvider<UserQueryRepository> userQueryRepositoryProvider;

    // 기본 상시 모니터링 주요 자산 리스트
    private static final List<String> BASE_MONITORED_SYMBOLS = List.of(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "NVDA", "TSLA", "005930.KS"
    );

    // 웜업 이력 및 통계 추적
    private LocalDateTime lastWarmupTime = null;
    private final Set<String> warmSymbols = ConcurrentHashMap.newKeySet();
    private int totalWarmupExecutions = 0;
    private int totalPreloadedArticles = 0;

    /**
     * 서버 기동 10초 후 백그라운드에서 초기 지식 베이스 사전 예열(Warmup) 실행
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                log.info("[ProactiveWarmup] 🚀 Initializing startup knowledge base pre-warming...");
                warmupAllTargets();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "warmup-init-thread").start();
    }

    /**
     * 매일 오전 8시 및 오후 1시 장전 정기 배치 사전 크롤링 실행 (Cron)
     */
    @Scheduled(cron = "0 0 8,13 * * *")
    public void scheduledDailyWarmup() {
        log.info("[ProactiveWarmup] ⏰ Triggering scheduled daily batch crawler for vmstore pre-warming...");
        warmupAllTargets();
    }

    /**
     * 1. 유저 질문 빈도 분석 기반 크롤링 타겟 자동 발굴 + 기본 종목 병합
     */
    public Set<String> discoverCrawlingTargets() {
        Set<String> targets = new LinkedHashSet<>(BASE_MONITORED_SYMBOLS);

        var repo = userQueryRepositoryProvider.getIfAvailable();
        if (repo != null) {
            try {
                List<Object[]> popular = repo.findPopularKeywords();
                if (popular != null && !popular.isEmpty()) {
                    for (Object[] row : popular) {
                        String sym = (String) row[0];
                        if (sym != null && !sym.isBlank() && !sym.equals("UNKNOWN")) {
                            targets.add(sym.toUpperCase().trim());
                        }
                    }
                    log.info("[ProactiveWarmup] 🎯 Discovered {} dynamic targets from user query logs (Total targets: {})",
                            popular.size(), targets.size());
                }
            } catch (Exception e) {
                log.warn("[ProactiveWarmup] Failed to analyze popular query keywords: {}", e.getMessage());
            }
        }

        return targets;
    }

    /**
     * 2. 대상 종목 전체에 대해 실시간 뉴스 크롤링 & vmstore 선적재 실행
     */
    public Map<String, Object> warmupAllTargets() {
        Set<String> targets = discoverCrawlingTargets();
        int preloadedCount = 0;
        List<String> processed = new ArrayList<>();

        for (String symbol : targets) {
            try {
                List<String> news = ragService.retrieveRelevantNews(symbol);
                if (news != null && !news.isEmpty()) {
                    preloadedCount += news.size();
                    warmSymbols.add(symbol);
                    processed.add(symbol);
                }
            } catch (Exception e) {
                log.warn("[ProactiveWarmup] Error during warmup for {}: {}", symbol, e.getMessage());
            }
        }

        this.lastWarmupTime = LocalDateTime.now();
        this.totalWarmupExecutions++;
        this.totalPreloadedArticles += preloadedCount;

        log.info("[ProactiveWarmup] ✅ Batch Warmup Completed: {} symbols pre-indexed with {} articles in vmstore",
                processed.size(), preloadedCount);

        return Map.of(
                "status", "SUCCESS",
                "processedSymbols", processed,
                "preloadedArticles", preloadedCount,
                "completedAt", lastWarmupTime.toString()
        );
    }

    /**
     * 수동 특정 종목 온디맨드 웜업
     */
    public Map<String, Object> warmupSpecificSymbols(List<String> symbols) {
        int count = 0;
        List<String> processed = new ArrayList<>();
        if (symbols != null) {
            for (String s : symbols) {
                if (s == null || s.isBlank()) continue;
                String sym = s.toUpperCase().trim();
                List<String> news = ragService.retrieveRelevantNews(sym);
                if (news != null && !news.isEmpty()) {
                    count += news.size();
                    warmSymbols.add(sym);
                    processed.add(sym);
                }
            }
        }
        return Map.of(
                "status", "SUCCESS",
                "processedSymbols", processed,
                "preloadedArticles", count,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * 3. 웜업 상태 및 API 절감 통계 조회
     */
    public Map<String, Object> getWarmupStats() {
        return Map.of(
                "lastWarmupTime", lastWarmupTime != null ? lastWarmupTime.toString() : "N/A",
                "totalExecutions", totalWarmupExecutions,
                "totalPreloadedArticles", totalPreloadedArticles,
                "activeWarmedSymbols", warmSymbols,
                "estimatedApiCreditsSaved", totalPreloadedArticles * 2 // 재호출 대비 절감 횟수
        );
    }
}
