package com.tem.spring.gamification.repository;

import com.tem.spring.gamification.entity.PredictionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PredictionRepository extends JpaRepository<PredictionEntity, Long> {

    @Query("SELECT p FROM PredictionEntity p WHERE p.user.id = :userId ORDER BY p.createdAt DESC")
    List<PredictionEntity> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT p FROM PredictionEntity p WHERE p.status = 'PENDING' AND p.targetTime <= :now")
    List<PredictionEntity> findDuePendingPredictions(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(p) FROM PredictionEntity p WHERE p.symbol = :symbol AND p.predictedDirection = 'BULL'")
    long countBullVotesBySymbol(@Param("symbol") String symbol);

    @Query("SELECT COUNT(p) FROM PredictionEntity p WHERE p.symbol = :symbol AND p.predictedDirection = 'BEAR'")
    long countBearVotesBySymbol(@Param("symbol") String symbol);
}
