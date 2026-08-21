package com.tem.spring.community.controller;

import com.tem.spring.community.dto.CreatePostRequest;
import com.tem.spring.community.dto.LikeResponse;
import com.tem.spring.community.dto.PostResponse;
import com.tem.spring.community.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 커뮤니티 분석 피드 및 AI 팩트체크/좋아요 토큰 보상 REST API 컨트롤러
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * 1. 퀀트/금융 분석 게시글 작성 API (AI 팩트체크 & ChromaDB 자동 색인 & 5 AETHER 보상)
     */
    @PostMapping
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.ok(postService.createPost(request));
    }

    /**
     * 2. 게시글 좋아요/취소 토글 API (좋아요 시 작성자에게 1 AETHER 보상)
     */
    @PostMapping("/{postId}/like")
    public ResponseEntity<LikeResponse> toggleLike(
            @PathVariable Long postId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(postService.toggleLike(postId, userId));
    }

    /**
     * 3. 커뮤니티 최신 분석 피드 목록 조회 API
     */
    @GetMapping
    public ResponseEntity<List<PostResponse>> getRecentPosts(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Long currentUserId,
            @RequestParam(defaultValue = "15") int limit) {
        return ResponseEntity.ok(postService.getRecentPosts(symbol, currentUserId, limit));
    }
}
