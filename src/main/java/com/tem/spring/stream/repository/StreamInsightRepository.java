package com.tem.spring.stream.repository;

import com.tem.spring.stream.entity.StreamInsightEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StreamInsightRepository extends JpaRepository<StreamInsightEntity, Long> {

    List<StreamInsightEntity> findByChannelIdOrderByTimestampDesc(Long channelId, Pageable pageable);

    List<StreamInsightEntity> findBySymbolOrderByTimestampDesc(String symbol, Pageable pageable);

    List<StreamInsightEntity> findAllByOrderByTimestampDesc(Pageable pageable);
}
