package com.tem.spring.bot.repository;

import com.tem.spring.bot.entity.BotSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BotSubscriptionRepository extends JpaRepository<BotSubscriptionEntity, Long> {

    @Query("SELECT s FROM BotSubscriptionEntity s WHERE s.user.id = :userId AND s.status = 'ACTIVE' AND s.endDate > :now ORDER BY s.endDate DESC")
    List<BotSubscriptionEntity> findActiveSubscriptionsByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    Optional<BotSubscriptionEntity> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<BotSubscriptionEntity> findByStatusAndEndDateBefore(String status, LocalDateTime now);
}
