package com.tem.spring.gamification.repository;

import com.tem.spring.gamification.entity.UserPredictionStatsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPredictionStatsRepository extends JpaRepository<UserPredictionStatsEntity, Long> {

    @Query("SELECT s FROM UserPredictionStatsEntity s WHERE s.user.id = :userId")
    Optional<UserPredictionStatsEntity> findByUserId(@Param("userId") Long userId);

    @Query("SELECT s FROM UserPredictionStatsEntity s ORDER BY s.winRatePct DESC, s.currentStreak DESC, s.totalPredictions DESC")
    List<UserPredictionStatsEntity> findTopLeaderboard(Pageable pageable);
}
