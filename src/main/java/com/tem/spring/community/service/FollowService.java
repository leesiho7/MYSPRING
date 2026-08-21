package com.tem.spring.community.service;

import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.community.dto.ExpertProfileResponse;
import com.tem.spring.community.dto.FollowResponse;
import com.tem.spring.community.entity.FollowEntity;
import com.tem.spring.community.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 커뮤니티 팔로우/언팔로우 및 금융 전문가 평판 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    @Transactional
    public FollowResponse toggleFollow(Long followerId, Long targetUserId) {
        log.info("[FollowService] Toggle follow: User {} -> Target {}", followerId, targetUserId);

        if (followerId.equals(targetUserId)) {
            return FollowResponse.builder()
                    .success(false)
                    .message("자기 자신을 팔로우할 수 없습니다.")
                    .build();
        }

        UserEntity follower = userRepository.findById(followerId)
                .orElseThrow(() -> new IllegalArgumentException("팔로워 유저를 찾을 수 없습니다. ID: " + followerId));

        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("대상 유저를 찾을 수 없습니다. ID: " + targetUserId));

        boolean isAlreadyFollowing = followRepository.isFollowing(followerId, targetUserId);

        if (isAlreadyFollowing) {
            // 언팔로우 처리
            followRepository.deleteByFollowerAndFollowing(followerId, targetUserId);
            target.setReputationScore(Math.max(0, target.getReputationScore() - 5)); // 평판 점수 -5P
            userRepository.save(target);

            long followerCount = followRepository.countFollowersByUserId(targetUserId);
            long followingCount = followRepository.countFollowingsByUserId(targetUserId);

            return FollowResponse.builder()
                    .success(true)
                    .message(target.getNickname() + " 님을 언팔로우했습니다.")
                    .following(false)
                    .followerCount(followerCount)
                    .followingCount(followingCount)
                    .targetReputationScore(target.getReputationScore())
                    .build();
        } else {
            // 신규 팔로우 처리
            FollowEntity follow = FollowEntity.builder()
                    .follower(follower)
                    .following(target)
                    .createdAt(LocalDateTime.now())
                    .build();
            followRepository.save(follow);

            target.setReputationScore(target.getReputationScore() + 5); // 팔로워 획득 보상 +5P
            userRepository.save(target);

            long followerCount = followRepository.countFollowersByUserId(targetUserId);
            long followingCount = followRepository.countFollowingsByUserId(targetUserId);

            return FollowResponse.builder()
                    .success(true)
                    .message(target.getNickname() + " 님을 팔로우했습니다! (평판 +5P)")
                    .following(true)
                    .followerCount(followerCount)
                    .followingCount(followingCount)
                    .targetReputationScore(target.getReputationScore())
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public FollowResponse getFollowStats(Long targetUserId, Long currentUserId) {
        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. ID: " + targetUserId));

        long followerCount = followRepository.countFollowersByUserId(targetUserId);
        long followingCount = followRepository.countFollowingsByUserId(targetUserId);
        boolean isFollowing = currentUserId != null && followRepository.isFollowing(currentUserId, targetUserId);

        return FollowResponse.builder()
                .success(true)
                .message("조회 성공")
                .following(isFollowing)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .targetReputationScore(target.getReputationScore())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ExpertProfileResponse> getTopExperts(Long currentUserId, int limit) {
        List<UserEntity> topUsers = userRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "reputationScore"))
        ).getContent();

        return topUsers.stream().map(u -> {
            long followerCount = followRepository.countFollowersByUserId(u.getId());
            long followingCount = followRepository.countFollowingsByUserId(u.getId());
            boolean isFollowed = currentUserId != null && followRepository.isFollowing(currentUserId, u.getId());

            return ExpertProfileResponse.builder()
                    .userId(u.getId())
                    .username(u.getUsername())
                    .nickname(u.getNickname())
                    .walletAddress(u.getWalletAddress())
                    .reputationScore(u.getReputationScore())
                    .role(u.getRole())
                    .followerCount(followerCount)
                    .followingCount(followingCount)
                    .isFollowedByMe(isFollowed)
                    .build();
        }).toList();
    }
}
