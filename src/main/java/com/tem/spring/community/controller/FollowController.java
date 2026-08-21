package com.tem.spring.community.controller;

import com.tem.spring.community.dto.ExpertProfileResponse;
import com.tem.spring.community.dto.FollowResponse;
import com.tem.spring.community.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 커뮤니티 팔로우/언팔로우 및 금융 분석가 리더보드 REST API 컨트롤러
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /**
     * 1. 특정 유저 팔로우 / 언팔로우 토글 API
     */
    @PostMapping("/follow/{targetUserId}")
    public ResponseEntity<FollowResponse> toggleFollow(
            @PathVariable Long targetUserId,
            @RequestParam Long followerId) {
        FollowResponse response = followService.toggleFollow(followerId, targetUserId);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 2. 특정 유저의 팔로워/팔로잉 수 및 내 팔로우 여부 통계 조회 API
     */
    @GetMapping("/follow/{userId}/stats")
    public ResponseEntity<FollowResponse> getFollowStats(
            @PathVariable Long userId,
            @RequestParam(required = false) Long currentUserId) {
        return ResponseEntity.ok(followService.getFollowStats(userId, currentUserId));
    }

    /**
     * 3. 커뮤니티 상위 퀀트/금융 전문가 리더보드 조회 API
     */
    @GetMapping("/experts")
    public ResponseEntity<List<ExpertProfileResponse>> getTopExperts(
            @RequestParam(required = false) Long currentUserId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(followService.getTopExperts(currentUserId, limit));
    }
}
