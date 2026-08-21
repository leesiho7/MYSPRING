package com.tem.spring.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletSummaryResponse {
    private Long userId;
    private String nickname;
    private double tokenBalance;
    private String walletAddress;
    private String bybitUid;
    private int reputationScore;
    private List<WithdrawResponse> recentWithdrawals;
}
