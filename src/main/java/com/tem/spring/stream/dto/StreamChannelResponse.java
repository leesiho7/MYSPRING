package com.tem.spring.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChannelResponse {

    private Long channelId;
    private String channelName;
    private String streamTitle;
    private String streamType; // LIVE_STREAM, FINANCIAL_NEWS, VOD_TUTORIAL
    private String platform; // YOUTUBE, TWITCH, HLS_DIRECT
    private String embedUrl;
    private String category; // CRYPTO, US_STOCK, MACRO, QUANT
    private String thumbnailUrl;
    private int viewerCount;
    private String streamerName;
    private boolean isLiveNow;
    private String liveSentiment;
    private String targetSymbol;
    private int likeCount;
    private int dislikeCount;
    private int commentCount;
    private String userReaction; // LIKE, DISLIKE, NONE
    private LocalDateTime createdAt;
}
