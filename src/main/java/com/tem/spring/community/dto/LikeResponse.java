package com.tem.spring.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikeResponse {
    private boolean success;
    private String message;
    private boolean liked;
    private int likeCount;
    private double currentTokenReward;
}
