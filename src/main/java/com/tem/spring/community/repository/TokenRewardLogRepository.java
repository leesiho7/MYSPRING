package com.tem.spring.community.repository;

import com.tem.spring.community.entity.TokenRewardLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TokenRewardLogRepository extends JpaRepository<TokenRewardLogEntity, Long> {

    @Query("SELECT r FROM TokenRewardLogEntity r WHERE r.recipient.id = :recipientId ORDER BY r.rewardedAt DESC")
    List<TokenRewardLogEntity> findRecentRewardsByRecipientId(@Param("recipientId") Long recipientId, Pageable pageable);
}
