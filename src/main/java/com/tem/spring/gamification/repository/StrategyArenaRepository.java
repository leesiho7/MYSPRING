package com.tem.spring.gamification.repository;

import com.tem.spring.gamification.entity.StrategyArenaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StrategyArenaRepository extends JpaRepository<StrategyArenaEntity, Long> {

    @Query("SELECT a FROM StrategyArenaEntity a WHERE a.season = :season ORDER BY a.currentReturnPct DESC, a.profitFactor DESC")
    List<StrategyArenaEntity> findTopArenaLeaderboard(@Param("season") String season, Pageable pageable);

    @Query("SELECT a FROM StrategyArenaEntity a WHERE a.author.id = :authorId ORDER BY a.createdAt DESC")
    List<StrategyArenaEntity> findByAuthorId(@Param("authorId") Long authorId);
}
