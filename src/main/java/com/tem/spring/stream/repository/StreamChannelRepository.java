package com.tem.spring.stream.repository;

import com.tem.spring.stream.entity.StreamChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StreamChannelRepository extends JpaRepository<StreamChannelEntity, Long> {

    List<StreamChannelEntity> findByIsLiveNowOrderByViewerCountDesc(boolean isLiveNow);

    List<StreamChannelEntity> findByCategoryOrderByViewerCountDesc(String category);

    List<StreamChannelEntity> findByTargetSymbolOrderByViewerCountDesc(String targetSymbol);

    List<StreamChannelEntity> findAllByOrderByViewerCountDesc();
}
