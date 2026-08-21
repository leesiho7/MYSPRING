package com.tem.spring.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private Long postId;
    private Long authorId;
    private String authorNickname;
    private String authorWallet;
    private int authorReputation;
    private String authorRole;
    private String symbol;
    private String title;
    private String content;
    private String sentimentBias;
    private double targetPrice;
    private String backtestSnapshot;
    private boolean aiFactChecked;
    private String aiFactCheckSummary;
    private int likeCount;
    private boolean likedByMe;
    private double rewardTokenAmount;
    private LocalDateTime createdAt;
}
