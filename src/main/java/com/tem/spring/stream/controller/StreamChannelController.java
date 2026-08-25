package com.tem.spring.stream.controller;

import com.tem.spring.stream.dto.*;
import com.tem.spring.stream.service.LiveStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 실시간 트레이딩 방송 & 좋아요/싫어요/댓글 REST API 컨트롤러
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class StreamChannelController {

    private final LiveStreamService streamService;

    /**
     * 1. 전체 또는 카테고리별 실시간 방송 채널 목록 조회 API (유저별 반응 여부 포함)
     */
    @GetMapping("/channels")
    public ResponseEntity<List<StreamChannelResponse>> getChannels(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(streamService.getChannels(category, symbol, userId));
    }

    /**
     * 2. 단일 채널 상세 정보 조회 API
     */
    @GetMapping("/channels/{channelId}")
    public ResponseEntity<StreamChannelResponse> getChannel(
            @PathVariable Long channelId,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(streamService.getChannel(channelId, userId));
    }

    /**
     * 3. 커스텀 유튜브/트위치 라이브 채널 추가 등록 API
     */
    @PostMapping("/channels")
    public ResponseEntity<StreamChannelResponse> addChannel(@Valid @RequestBody AddChannelRequest request) {
        return ResponseEntity.ok(streamService.addChannel(request));
    }

    /**
     * 4. 실시간 방송 좋아요 / 싫어요 토글 API (좋아요 시 0.5 AETHER 토큰 보상)
     */
    @PostMapping("/channels/{channelId}/reaction")
    public ResponseEntity<StreamReactionResponse> toggleReaction(
            @PathVariable Long channelId,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "LIKE") String reactionType) {
        return ResponseEntity.ok(streamService.toggleReaction(channelId, userId, reactionType));
    }

    /**
     * 5. 실시간 방송에 댓글/분석평 작성 API (1.0 AETHER 토큰 보상)
     */
    @PostMapping("/channels/{channelId}/comments")
    public ResponseEntity<StreamCommentResponse> addComment(
            @PathVariable Long channelId,
            @Valid @RequestBody CreateStreamCommentRequest request) {
        return ResponseEntity.ok(streamService.addComment(channelId, request));
    }

    /**
     * 6. 실시간 방송 댓글 목록 조회 API
     */
    @GetMapping("/channels/{channelId}/comments")
    public ResponseEntity<List<StreamCommentResponse>> getComments(
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(streamService.getComments(channelId, limit));
    }

    /**
     * 7. 실시간 방송 내 타임스탬프 알파 발언 목록 조회 API
     */
    @GetMapping("/insights")
    public ResponseEntity<List<StreamInsightResponse>> getStreamInsights(
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(streamService.getStreamInsights(channelId, symbol, limit));
    }
}
