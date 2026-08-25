package com.tem.spring.bot.repository;

import com.tem.spring.bot.entity.BotInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BotInstanceRepository extends JpaRepository<BotInstanceEntity, Long> {

    List<BotInstanceEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<BotInstanceEntity> findByIdAndUserId(Long id, Long userId);

    List<BotInstanceEntity> findByStatus(String status);
}
