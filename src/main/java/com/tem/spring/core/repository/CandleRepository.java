package com.tem.spring.core.repository;

import com.tem.spring.core.entity.CandleEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    Optional<CandleEntity> findBySymbolAndTimestamp(String symbol, ZonedDateTime timestamp);

    @Query("SELECT c FROM CandleEntity c WHERE c.symbol = :symbol ORDER BY c.timestamp DESC")
    List<CandleEntity> findRecentCandlesBySymbol(@Param("symbol") String symbol, Pageable pageable);

    @Query("SELECT c.timestamp FROM CandleEntity c WHERE c.symbol = :symbol AND c.timestamp IN :timestamps")
    java.util.Set<ZonedDateTime> findExistingTimestamps(@Param("symbol") String symbol, @Param("timestamps") java.util.Collection<ZonedDateTime> timestamps);

    boolean existsBySymbolAndTimestamp(String symbol, ZonedDateTime timestamp);
}
