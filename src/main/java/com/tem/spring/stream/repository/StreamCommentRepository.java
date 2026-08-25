package com.tem.spring.stream.repository;

import com.tem.spring.stream.entity.StreamCommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StreamCommentRepository extends JpaRepository<StreamCommentEntity, Long> {

    List<StreamCommentEntity> findByChannelIdOrderByCreatedAtDesc(Long channelId, Pageable pageable);

    long countByChannelId(Long channelId);
}
