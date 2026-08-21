package com.tem.spring.community.repository;

import com.tem.spring.community.entity.PostLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLikeEntity, Long> {

    @Query("SELECT COUNT(l) > 0 FROM PostLikeEntity l WHERE l.user.id = :userId AND l.post.id = :postId")
    boolean isLiked(@Param("userId") Long userId, @Param("postId") Long postId);

    @Query("SELECT l FROM PostLikeEntity l WHERE l.user.id = :userId AND l.post.id = :postId")
    Optional<PostLikeEntity> findByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    @Modifying
    @Query("DELETE FROM PostLikeEntity l WHERE l.user.id = :userId AND l.post.id = :postId")
    void deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    @Query("SELECT COUNT(l) FROM PostLikeEntity l WHERE l.post.id = :postId")
    int countLikesByPostId(@Param("postId") Long postId);
}
