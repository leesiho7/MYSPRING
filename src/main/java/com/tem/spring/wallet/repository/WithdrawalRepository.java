package com.tem.spring.wallet.repository;

import com.tem.spring.wallet.entity.WithdrawalEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WithdrawalRepository extends JpaRepository<WithdrawalEntity, Long> {

    @Query("SELECT w FROM WithdrawalEntity w WHERE w.user.id = :userId ORDER BY w.requestedAt DESC")
    List<WithdrawalEntity> findByUserId(@Param("userId") Long userId, Pageable pageable);
}
