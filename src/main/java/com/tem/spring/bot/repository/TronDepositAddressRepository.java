package com.tem.spring.bot.repository;

import com.tem.spring.bot.entity.TronDepositAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TronDepositAddressRepository extends JpaRepository<TronDepositAddressEntity, Long> {

    Optional<TronDepositAddressEntity> findByUserId(Long userId);

    Optional<TronDepositAddressEntity> findByTronAddress(String tronAddress);

    boolean existsByUserId(Long userId);
}
