package com.tem.spring.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotSubscriptionResponse {

    private boolean success;
    private String message;
    private Long subscriptionId;
    private Long userId;
    private String planName;
    private double amountUsdt;
    private String paymentNetwork;
    private String txHash;
    private String status; // ACTIVE, EXPIRED, PENDING
    private boolean active;
    private long remainingDays;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
