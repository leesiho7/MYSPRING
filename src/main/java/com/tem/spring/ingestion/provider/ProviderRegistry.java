package com.tem.spring.ingestion.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * OpenBB의 Provider Registry 패턴 구현: 등록된 Provider 중 적절한 어댑터를 동적 검색
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderRegistry {

    private final List<DataProvider> dataProviders;

    public Optional<DataProvider> getProviderFor(String symbol) {
        return dataProviders.stream()
                .filter(p -> p.supports(symbol))
                .findFirst();
    }

    public List<DataProvider> getAllProviders() {
        return List.copyOf(dataProviders);
    }
}
