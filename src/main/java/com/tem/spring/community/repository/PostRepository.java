package com.tem.spring.community.repository;

import com.tem.spring.community.entity.PostEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<PostEntity, Long> {

    @Query("SELECT p FROM PostEntity p WHERE p.symbol = :symbol ORDER BY p.createdAt DESC")
    List<PostEntity> findRecentPostsBySymbol(@Param("symbol") String symbol, Pageable pageable);

    @Query("SELECT p FROM PostEntity p ORDER BY p.createdAt DESC")
    List<PostEntity> findAllRecentPosts(Pageable pageable);

    @Query("SELECT p FROM PostEntity p WHERE p.author.id = :authorId ORDER BY p.createdAt DESC")
    List<PostEntity> findPostsByAuthorId(@Param("authorId") Long authorId, Pageable pageable);

    @Query("SELECT COUNT(p) FROM PostEntity p WHERE p.author.id = :authorId")
    long countByAuthorId(@Param("authorId") Long authorId);
}
