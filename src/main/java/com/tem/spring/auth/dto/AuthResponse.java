package com.tem.spring.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private boolean success;
    private String message;
    private Long userId;
    private String username;
    private String nickname;
    private String walletAddress;
    private int reputationScore;
    private double tokenBalance;
    private String role;
    private String accessToken;
}
