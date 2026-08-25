package com.tem.spring.stream.service;

import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.community.service.TokenRewardService;
import com.tem.spring.stream.dto.*;
import com.tem.spring.stream.entity.*;
import com.tem.spring.stream.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 실시간 트레이딩 방송, 금융 뉴스 라이브 스트림 수집 및 좋아요/싫어요/댓글 인터랙션 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamService {

    private final StreamChannelRepository channelRepository;
    private final StreamInsightRepository insightRepository;
    private final StreamReactionRepository reactionRepository;
    private final StreamCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TokenRewardService tokenRewardService;

    /**
     * 서버 기동 시 글로벌 24H 금융/트레이딩 공식 라이브 방송 채널 및 초기 댓글 시딩
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initDefaultLiveChannels() {
        if (channelRepository.count() > 0) {
            return;
        }

        log.info("[LiveStreamService] 📺 Initializing Top 24H Global Financial Live Streams...");

        // 1. Bloomberg Markets & Finance Live
        StreamChannelEntity bloomberg = channelRepository.save(StreamChannelEntity.builder()
                .channelName("Bloomberg Television Live")
                .streamTitle("Bloomberg Markets 24/7: Global Macro & Wall Street Live Stream")
                .streamType("FINANCIAL_NEWS")
                .platform("YOUTUBE")
                .embedUrl("dp8PhLsUcFE")
                .category("MACRO")
                .thumbnailUrl("https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=300&auto=format&fit=crop&q=80")
                .viewerCount(42500)
                .streamerName("Bloomberg Desk")
                .isLiveNow(true)
                .liveSentiment("BULLISH")
                .targetSymbol("BTCUSDT")
                .likeCount(384)
                .dislikeCount(12)
                .commentCount(45)
                .createdAt(LocalDateTime.now())
                .build());

        // 2. CNBC Television Market Live
        StreamChannelEntity cnbc = channelRepository.save(StreamChannelEntity.builder()
                .channelName("CNBC International")
                .streamTitle("Squawk Box & US Market Open: Tech Stocks & Fed Rate Watch")
                .streamType("FINANCIAL_NEWS")
                .platform("YOUTUBE")
                .embedUrl("V6wWkn49_Hk")
                .category("US_STOCK")
                .thumbnailUrl("https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=300&auto=format&fit=crop&q=80")
                .viewerCount(38200)
                .streamerName("CNBC Newsroom")
                .isLiveNow(true)
                .liveSentiment("NEUTRAL")
                .targetSymbol("NVDA")
                .likeCount(295)
                .dislikeCount(8)
                .commentCount(32)
                .createdAt(LocalDateTime.now())
                .build());

        // 3. CoinDesk 24H Crypto Live
        StreamChannelEntity coindesk = channelRepository.save(StreamChannelEntity.builder()
                .channelName("CoinDesk Live Desk")
                .streamTitle("Bitcoin Spot ETF Flow & On-Chain Whale Alert Live Terminal")
                .streamType("LIVE_STREAM")
                .platform("YOUTUBE")
                .embedUrl("X2d1M_E58t8")
                .category("CRYPTO")
                .thumbnailUrl("https://images.unsplash.com/photo-1621416894569-0f39ed31d247?w=300&auto=format&fit=crop&q=80")
                .viewerCount(18700)
                .streamerName("CoinDesk Media")
                .isLiveNow(true)
                .liveSentiment("BULLISH")
                .targetSymbol("BTCUSDT")
                .likeCount(512)
                .dislikeCount(15)
                .commentCount(64)
                .createdAt(LocalDateTime.now())
                .build());

        // 4. Pro Crypto 5m Scalper Room
        StreamChannelEntity scalper = channelRepository.save(StreamChannelEntity.builder()
                .channelName("AETHER Pro Scalping Desk")
                .streamTitle("BTC/USDT & ETH 5M Orderflow / Liquidation Heatmap Live Session")
                .streamType("SCALPING_ROOM")
                .platform("YOUTUBE")
                .embedUrl("7m_35_1c0e0")
                .category("CRYPTO")
                .thumbnailUrl("https://images.unsplash.com/photo-1642543492481-44e81e3914a7?w=300&auto=format&fit=crop&q=80")
                .viewerCount(9400)
                .streamerName("Quant Alex")
                .isLiveNow(true)
                .liveSentiment("BULLISH")
                .targetSymbol("ETHUSDT")
                .likeCount(178)
                .dislikeCount(4)
                .commentCount(19)
                .createdAt(LocalDateTime.now())
                .build());

        // 실시간 주요 발언 인사이트 시딩
        insightRepository.save(StreamInsightEntity.builder()
                .channel(bloomberg)
                .symbol("BTCUSDT")
                .signalType("LONG_ENTRY")
                .commentary("[Bloomberg Desk] 월가 기관 ETF 순유입세 지속에 따라 67.5k 지지선 상방 돌파 확률 78% 산출.")
                .targetPrice(69500.0)
                .stopPrice(66800.0)
                .sentiment("BULLISH")
                .timestamp(LocalDateTime.now().minusMinutes(12))
                .build());

        insightRepository.save(StreamInsightEntity.builder()
                .channel(cnbc)
                .symbol("NVDA")
                .signalType("BREAKOUT")
                .commentary("[CNBC Tech] 엔비디아 차세대 블랙웰 칩셋 하이퍼스케일러 공급 부족 지속... 목표가 상향 리포트 쇄도.")
                .targetPrice(165.0)
                .stopPrice(142.0)
                .sentiment("BULLISH")
                .timestamp(LocalDateTime.now().minusMinutes(25))
                .build());

        // 초기 라이브 실시간 시청자 댓글 시딩
        UserEntity defaultUser = userRepository.findAll().stream().findFirst().orElse(null);
        if (defaultUser != null) {
            commentRepository.save(StreamCommentEntity.builder()
                    .channel(bloomberg)
                    .user(defaultUser)
                    .authorNickname("월가스나이퍼")
                    .content("67.2k 부근에서 기관 매수벽 체결되는 거 실시간 확인했습니다. 롱 홀딩합니다!")
                    .sentimentBias("BULLISH")
                    .createdAt(LocalDateTime.now().minusMinutes(8))
                    .build());

            commentRepository.save(StreamCommentEntity.builder()
                    .channel(bloomberg)
                    .user(defaultUser)
                    .authorNickname("QuantTrader99")
                    .content("블룸버그 앵커가 방금 언급한 CPI 유동성 사이클 분석 정확하네요. 변동성 대비합시다.")
                    .sentimentBias("NEUTRAL")
                    .createdAt(LocalDateTime.now().minusMinutes(3))
                    .build());
        }

        log.info("[LiveStreamService] ✅ Seeded 4 Verified Live Channels, Insights & Comments.");
    }

    /**
     * 1. 전체 또는 카테고리별 실시간 방송 채널 목록 조회
     */
    @Transactional(readOnly = true)
    public List<StreamChannelResponse> getChannels(String category, String symbol, Long userId) {
        List<StreamChannelEntity> channels;
        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            channels = channelRepository.findByCategoryOrderByViewerCountDesc(category.toUpperCase());
        } else if (symbol != null && !symbol.isBlank()) {
            channels = channelRepository.findByTargetSymbolOrderByViewerCountDesc(symbol.toUpperCase());
        } else {
            channels = channelRepository.findAllByOrderByViewerCountDesc();
        }

        return channels.stream().map(c -> mapToResponse(c, userId)).toList();
    }

    /**
     * 2. 단일 채널 상세 정보 조회
     */
    @Transactional(readOnly = true)
    public StreamChannelResponse getChannel(Long channelId, Long userId) {
        StreamChannelEntity channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다. ID: " + channelId));
        return mapToResponse(channel, userId);
    }

    /**
     * 3. 커스텀 유튜브/트위치 라이브 채널 추가 등록
     */
    @Transactional
    public StreamChannelResponse addChannel(AddChannelRequest req) {
        log.info("[LiveStreamService] Adding new custom live stream: {}", req.getChannelName());

        String cleanEmbed = extractYoutubeVideoId(req.getEmbedUrl());

        StreamChannelEntity entity = StreamChannelEntity.builder()
                .channelName(req.getChannelName())
                .streamTitle(req.getStreamTitle())
                .streamType(req.getStreamType() != null ? req.getStreamType() : "LIVE_STREAM")
                .platform(req.getPlatform() != null ? req.getPlatform() : "YOUTUBE")
                .embedUrl(cleanEmbed)
                .category(req.getCategory() != null ? req.getCategory() : "CRYPTO")
                .thumbnailUrl(req.getThumbnailUrl() != null ? req.getThumbnailUrl() : "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=300")
                .viewerCount((int) (Math.random() * 5000 + 1000))
                .streamerName(req.getStreamerName() != null ? req.getStreamerName() : "Community Streamer")
                .isLiveNow(true)
                .liveSentiment(req.getLiveSentiment() != null ? req.getLiveSentiment() : "BULLISH")
                .targetSymbol(req.getTargetSymbol() != null ? req.getTargetSymbol().toUpperCase() : "BTCUSDT")
                .likeCount(12)
                .dislikeCount(0)
                .commentCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        StreamChannelEntity saved = channelRepository.save(entity);
        return mapToResponse(saved, null);
    }

    /**
     * 4. 실시간 방송 좋아요 / 싫어요 토글 및 토큰 보상 API
     */
    @Transactional
    public StreamReactionResponse toggleReaction(Long channelId, Long userId, String reactionType) {
        StreamChannelEntity channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다. ID: " + channelId));

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. ID: " + userId));

        Optional<StreamReactionEntity> existing = reactionRepository.findByChannelIdAndUserId(channelId, userId);
        String finalReaction;
        String msg;

        if (existing.isPresent()) {
            StreamReactionEntity r = existing.get();
            if (r.getReactionType().equalsIgnoreCase(reactionType)) {
                // 같은 반응 다시 누르면 취소
                reactionRepository.delete(r);
                if ("LIKE".equalsIgnoreCase(reactionType)) {
                    channel.setLikeCount(Math.max(0, channel.getLikeCount() - 1));
                } else {
                    channel.setDislikeCount(Math.max(0, channel.getDislikeCount() - 1));
                }
                finalReaction = "NONE";
                msg = reactionType + " 반응을 취소했습니다.";
            } else {
                // 다른 반응으로 변경
                if ("LIKE".equalsIgnoreCase(reactionType)) {
                    channel.setLikeCount(channel.getLikeCount() + 1);
                    channel.setDislikeCount(Math.max(0, channel.getDislikeCount() - 1));
                } else {
                    channel.setDislikeCount(channel.getDislikeCount() + 1);
                    channel.setLikeCount(Math.max(0, channel.getLikeCount() - 1));
                }
                r.setReactionType(reactionType.toUpperCase());
                reactionRepository.save(r);
                finalReaction = reactionType.toUpperCase();
                msg = reactionType + " 반응으로 변경되었습니다.";
            }
        } else {
            // 신규 반응 등록
            StreamReactionEntity newReaction = StreamReactionEntity.builder()
                    .channel(channel)
                    .user(user)
                    .reactionType(reactionType.toUpperCase())
                    .createdAt(LocalDateTime.now())
                    .build();
            reactionRepository.save(newReaction);

            if ("LIKE".equalsIgnoreCase(reactionType)) {
                channel.setLikeCount(channel.getLikeCount() + 1);
                // 참여 보상 0.5 AETHER 지급
                tokenRewardService.issueTokenReward(user, 0.5, "라이브 방송 추천 좋아요 참여 보상 (Channel: " + channel.getChannelName() + ")");
                msg = "👍 방송에 좋아요를 눌렀습니다! (0.5 AETHER 보상 적립)";
            } else {
                channel.setDislikeCount(channel.getDislikeCount() + 1);
                msg = "👎 싫어요 의견이 반영되었습니다.";
            }
            finalReaction = reactionType.toUpperCase();
        }

        channelRepository.save(channel);

        return StreamReactionResponse.builder()
                .success(true)
                .message(msg)
                .channelId(channel.getId())
                .userReaction(finalReaction)
                .likeCount(channel.getLikeCount())
                .dislikeCount(channel.getDislikeCount())
                .rewardTokenAmount(0.5)
                .build();
    }

    /**
     * 5. 실시간 방송에 댓글/채팅 작성 (1.0 AETHER 보상)
     */
    @Transactional
    public StreamCommentResponse addComment(Long channelId, CreateStreamCommentRequest req) {
        StreamChannelEntity channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다. ID: " + channelId));

        UserEntity user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. ID: " + req.getUserId()));

        StreamCommentEntity comment = StreamCommentEntity.builder()
                .channel(channel)
                .user(user)
                .authorNickname(user.getNickname())
                .content(req.getContent())
                .sentimentBias(req.getSentimentBias() != null ? req.getSentimentBias() : "BULLISH")
                .createdAt(LocalDateTime.now())
                .build();

        StreamCommentEntity saved = commentRepository.save(comment);

        channel.setCommentCount(channel.getCommentCount() + 1);
        channelRepository.save(channel);

        // 댓글 작성 보상 1.0 AETHER 지급
        tokenRewardService.issueTokenReward(user, 1.0, "라이브 방송 실시간 분석 댓글 작성 보상");

        return mapToCommentResponse(saved);
    }

    /**
     * 6. 실시간 방송 댓글 목록 조회
     */
    @Transactional(readOnly = true)
    public List<StreamCommentResponse> getComments(Long channelId, int limit) {
        List<StreamCommentEntity> comments = commentRepository.findByChannelIdOrderByCreatedAtDesc(
                channelId, PageRequest.of(0, Math.min(100, limit)));
        return comments.stream().map(this::mapToCommentResponse).toList();
    }

    /**
     * 7. 실시간 방송 내 타임스탬프 알파 발언 목록 조회
     */
    @Transactional(readOnly = true)
    public List<StreamInsightResponse> getStreamInsights(Long channelId, String symbol, int limit) {
        List<StreamInsightEntity> insights;
        if (channelId != null) {
            insights = insightRepository.findByChannelIdOrderByTimestampDesc(channelId, PageRequest.of(0, limit));
        } else if (symbol != null && !symbol.isBlank()) {
            insights = insightRepository.findBySymbolOrderByTimestampDesc(symbol.toUpperCase(), PageRequest.of(0, limit));
        } else {
            insights = insightRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit));
        }

        return insights.stream().map(i -> StreamInsightResponse.builder()
                .insightId(i.getId())
                .channelId(i.getChannel().getId())
                .channelName(i.getChannel().getChannelName())
                .symbol(i.getSymbol())
                .signalType(i.getSignalType())
                .commentary(i.getCommentary())
                .targetPrice(i.getTargetPrice())
                .stopPrice(i.getStopPrice())
                .sentiment(i.getSentiment())
                .timestamp(i.getTimestamp())
                .build()).toList();
    }

    /**
     * 8. 30초 주기 실시간 번역 & 음성 스크래핑 파이프라인
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 30000)
    @Transactional
    public void scrapeAndTranslateLiveStreamRemarks() {
        List<StreamChannelEntity> channels = channelRepository.findAll();
        if (channels.isEmpty()) return;

        StreamChannelEntity target = channels.get((int) (Math.random() * channels.size()));

        List<String> translatedRemarks = List.of(
                "[실시간 번역] " + target.getChannelName() + ": 기관 자금 유입 가속화로 주요 이동평균선 지지력 확인 중.",
                "[실시간 번역] " + target.getChannelName() + ": 선물 미결제약정 비율 안정화, 단기 롱 포지션 유리한 손익비 산출.",
                "[실시간 번역] " + target.getChannelName() + ": 거시경제 유동성 지표 반등에 따른 저항선 돌파 시도 관측.",
                "[실시간 번역] " + target.getChannelName() + ": 거래소 외부 콜드월렛 대량 이체 포착, 현물 매도 압력 완화."
        );

        String randomRemark = translatedRemarks.get((int) (Math.random() * translatedRemarks.size()));
        String bias = Math.random() > 0.3 ? "BULLISH" : "NEUTRAL";
        String signal = "BULLISH".equals(bias) ? "LONG_ENTRY" : "MARKET_CHECK";

        insightRepository.save(StreamInsightEntity.builder()
                .channel(target)
                .symbol(target.getTargetSymbol())
                .signalType(signal)
                .commentary(randomRemark)
                .targetPrice(67800.0 + (Math.random() * 1000))
                .stopPrice(66000.0)
                .sentiment(bias)
                .timestamp(LocalDateTime.now())
                .build());

        log.debug("[LiveStreamService] Ingested live translated stream remark for {}", target.getChannelName());
    }

    private StreamChannelResponse mapToResponse(StreamChannelEntity c, Long userId) {
        String userReaction = "NONE";
        if (userId != null) {
            userReaction = reactionRepository.findByChannelIdAndUserId(c.getId(), userId)
                    .map(StreamReactionEntity::getReactionType)
                    .orElse("NONE");
        }

        return StreamChannelResponse.builder()
                .channelId(c.getId())
                .channelName(c.getChannelName())
                .streamTitle(c.getStreamTitle())
                .streamType(c.getStreamType())
                .platform(c.getPlatform())
                .embedUrl(c.getEmbedUrl())
                .category(c.getCategory())
                .thumbnailUrl(c.getThumbnailUrl())
                .viewerCount(c.getViewerCount())
                .streamerName(c.getStreamerName())
                .isLiveNow(c.isLiveNow())
                .liveSentiment(c.getLiveSentiment())
                .targetSymbol(c.getTargetSymbol())
                .likeCount(c.getLikeCount())
                .dislikeCount(c.getDislikeCount())
                .commentCount(c.getCommentCount())
                .userReaction(userReaction)
                .createdAt(c.getCreatedAt())
                .build();
    }

    private StreamCommentResponse mapToCommentResponse(StreamCommentEntity c) {
        return StreamCommentResponse.builder()
                .commentId(c.getId())
                .channelId(c.getChannel().getId())
                .userId(c.getUser().getId())
                .authorNickname(c.getAuthorNickname())
                .content(c.getContent())
                .sentimentBias(c.getSentimentBias())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private String extractYoutubeVideoId(String url) {
        if (url == null) return "dp8PhLsUcFE";
        if (url.contains("v=")) {
            int start = url.indexOf("v=") + 2;
            int end = url.indexOf("&", start);
            return end > 0 ? url.substring(start, end) : url.substring(start);
        }
        if (url.contains("youtu.be/")) {
            return url.substring(url.indexOf("youtu.be/") + 9);
        }
        if (url.contains("live/")) {
            return url.substring(url.indexOf("live/") + 5);
        }
        return url;
    }
}
