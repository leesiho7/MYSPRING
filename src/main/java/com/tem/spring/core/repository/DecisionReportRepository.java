package com.tem.spring.core.repository;

import com.tem.spring.core.entity.DecisionReportEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionReportRepository extends JpaRepository<DecisionReportEntity, Long> {

    @Query("SELECT r FROM DecisionReportEntity r WHERE r.symbol = :symbol ORDER BY r.generatedAt DESC")
    List<DecisionReportEntity> findRecentReportsBySymbol(@Param("symbol") String symbol, Pageable pageable);
}
