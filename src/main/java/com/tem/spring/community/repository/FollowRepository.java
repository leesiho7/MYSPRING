package com.tem.spring.community.repository;

import com.tem.spring.community.entity.FollowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<FollowEntity, Long> {

    @Query("SELECT COUNT(f) > 0 FROM FollowEntity f WHERE f.follower.id = :followerId AND f.following.id = :followingId")
    boolean isFollowing(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    @Query("SELECT f FROM FollowEntity f WHERE f.follower.id = :followerId AND f.following.id = :followingId")
    Optional<FollowEntity> findByFollowerAndFollowing(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    @Modifying
    @Query("DELETE FROM FollowEntity f WHERE f.follower.id = :followerId AND f.following.id = :followingId")
    void deleteByFollowerAndFollowing(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    @Query("SELECT COUNT(f) FROM FollowEntity f WHERE f.following.id = :userId")
    long countFollowersByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(f) FROM FollowEntity f WHERE f.follower.id = :userId")
    long countFollowingsByUserId(@Param("userId") Long userId);
}
