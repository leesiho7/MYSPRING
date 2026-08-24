package com.tem.spring.ai.repository;

import com.tem.spring.ai.entity.UserQueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserQueryRepository extends JpaRepository<UserQueryEntity, Long> {

    List<UserQueryEntity> findTop50ByOrderByCreatedAtDesc();

    List<UserQueryEntity> findBySymbolOrderByCreatedAtDesc(String symbol);

    @Query("SELECT u.symbol, COUNT(u) as cnt FROM UserQueryEntity u GROUP BY u.symbol ORDER BY cnt DESC")
    List<Object[]> findPopularKeywords();
}
