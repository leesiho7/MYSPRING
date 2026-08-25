package com.tem.spring.bot.repository;

import com.tem.spring.bot.entity.BotExecutionLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BotExecutionLogRepository extends JpaRepository<BotExecutionLogEntity, Long> {

    List<BotExecutionLogEntity> findByInstanceIdOrderByTimestampDesc(Long instanceId, Pageable pageable);

    void deleteByInstanceId(Long instanceId);
}
