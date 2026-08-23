package com.tem.spring.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertProfileResponse {
    private Long userId;
    private String username;
    private String nickname;
    private String walletAddress;
    private int reputationScore;
    private String role;
    private long followerCount;
    private long followingCount;
    private long postCount;
    private boolean isFollowedByMe;
}
