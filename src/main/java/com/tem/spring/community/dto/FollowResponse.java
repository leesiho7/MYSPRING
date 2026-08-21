package com.tem.spring.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowResponse {
    private boolean success;
    private String message;
    private boolean following;          // 현재 팔로우 중 여부
    private long followerCount;         // 대상 유저의 총 팔로워 수
    private long followingCount;        // 대상 유저가 팔로우 중인 수
    private int targetReputationScore;  // 대상 유저의 갱신된 평판 점수
}
