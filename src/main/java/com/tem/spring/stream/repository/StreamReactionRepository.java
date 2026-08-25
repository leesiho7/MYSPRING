package com.tem.spring.stream.repository;

import com.tem.spring.stream.entity.StreamReactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StreamReactionRepository extends JpaRepository<StreamReactionEntity, Long> {

    Optional<StreamReactionEntity> findByChannelIdAndUserId(Long channelId, Long userId);

    long countByChannelIdAndReactionType(Long channelId, String reactionType);

    void deleteByChannelIdAndUserId(Long channelId, Long userId);
}
