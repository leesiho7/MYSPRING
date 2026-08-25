package com.tem.spring.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamReactionResponse {

    private boolean success;
    private String message;
    private Long channelId;
    private String userReaction; // LIKE, DISLIKE, NONE
    private int likeCount;
    private int dislikeCount;
    private double rewardTokenAmount;
}
