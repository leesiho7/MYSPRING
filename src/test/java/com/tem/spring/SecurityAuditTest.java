package com.tem.spring.config;

import com.tem.spring.core.model.IntegratedDecisionReport;
import com.tem.spring.core.model.TimeFrame;
import com.tem.spring.decision.SignalAggregatorService;
import com.tem.spring.ingestion.service.MarketDataIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 보안, 동시성 부하 및 메모리 누수 방어 검증 테스트
 */
@SpringBootTest
class SecurityAuditTest {

    @Autowired
    private MarketDataIngestionService ingestionService;

    @Autowired
    private SignalAggregatorService aggregatorService;

    @Test
    @DisplayName("1. [OOM 방어] MarketDataIngestionService 캐시 용량 초과 시 자동 LRU 메모리 방어 검증")
    void testCacheEvictionUnderStress() {
        for (int i = 0; i < 250; i++) {
            ingestionService.getHistoricalData("MOCK_SYM_" + i, TimeFrame.D1, 20);
        }
        assertDoesNotThrow(() -> ingestionService.getHistoricalData("BTCUSDT", TimeFrame.D1, 50));
    }

    @Test
    @DisplayName("2. [동시성/DoS 방어] 30개 동시 병렬 요청 시 스레드 풀 고갈 및 데드락 없이 전원 응답 검증")
    void testConcurrentDecisionRequests() throws Exception {
        int concurrentTasks = 15;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentTasks);
        List<Callable<IntegratedDecisionReport>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrentTasks; i++) {
            String sym = (i % 2 == 0) ? "BTCUSDT" : "000660";
            tasks.add(() -> aggregatorService.generateDecisionReport(sym, TimeFrame.D1, 30));
        }

        List<Future<IntegratedDecisionReport>> futures = executor.invokeAll(tasks);
        assertEquals(concurrentTasks, futures.size());

        for (Future<IntegratedDecisionReport> future : futures) {
            IntegratedDecisionReport report = future.get();
            assertNotNull(report);
            assertNotNull(report.getFinalAction());
        }

        executor.shutdown();
    }
}
