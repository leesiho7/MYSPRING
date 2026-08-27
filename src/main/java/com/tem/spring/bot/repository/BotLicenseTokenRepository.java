package com.tem.spring.bot.repository;

import com.tem.spring.bot.entity.BotLicenseTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BotLicenseTokenRepository extends JpaRepository<BotLicenseTokenEntity, Long> {

    Optional<BotLicenseTokenEntity> findByTokenString(String tokenString);

    Optional<BotLicenseTokenEntity> findByPaymentTxHash(String paymentTxHash);

    boolean existsByPaymentTxHash(String paymentTxHash);

    List<BotLicenseTokenEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT b FROM BotLicenseTokenEntity b WHERE b.user.id = :userId AND b.isActive = true AND b.expiredAt > :now ORDER BY b.expiredAt DESC")
    List<BotLicenseTokenEntity> findActiveTokensByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<BotLicenseTokenEntity> findByIsActiveTrueAndExpiredAtBefore(LocalDateTime now);

    Optional<BotLicenseTokenEntity> findByTelegramChatId(String telegramChatId);
}
