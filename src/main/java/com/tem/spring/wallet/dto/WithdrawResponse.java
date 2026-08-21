package com.tem.spring.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawResponse {
    private boolean success;
    private String message;
    private Long withdrawalId;
    private double withdrawnAmount;
    private double remainingBalance;
    private String destinationType;
    private String destinationAddress;
    private String proofTxHash;
    private String cryptographicProof;
    private LocalDateTime processedAt;
}
