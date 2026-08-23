package com.tem.spring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 트레이딩 시스템 전용 고성능/격리 비동기 스레드 풀 설정
 * 공용 ForkJoinPool 고갈 방지 및 DoS 공격 방어
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "tradingTaskExecutor")
    public Executor tradingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("Trading-Async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[AsyncConfig] Initialized dedicated tradingTaskExecutor (Core: 8, Max: 32, Queue: 200)");
        return executor;
    }
}
