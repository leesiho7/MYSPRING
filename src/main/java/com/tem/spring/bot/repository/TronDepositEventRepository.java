package com.tem.spring.bot.repository;

import com.tem.spring.bot.entity.TronDepositEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TronDepositEventRepository extends JpaRepository<TronDepositEventEntity, Long> {

    Optional<TronDepositEventEntity> findByTxId(String txId);

    boolean existsByTxId(String txId);

    List<TronDepositEventEntity> findByUserIdOrderByDetectedAtDesc(Long userId);

    List<TronDepositEventEntity> findByStatus(String status);
}
