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
public class BotInstanceResponse {

    private boolean success;
    private String message;
    private Long instanceId;
    private Long userId;
    private String botName;
    private String mode; // BEGINNER, DEVELOPER
    private String status; // STOPPED, RUNNING, ERROR, EXPIRED
    private String exchange;
    private String symbol;
    private String timeFrame;

    private String beginnerParamsJson;
    private String developerPythonCode;

    private int totalTrades;
    private int winningTrades;
    private double winRate;
    private double cumulativePnlPct;
    private double currentPositionUsdt;

    private LocalDateTime startedAt;
    private LocalDateTime stoppedAt;
    private LocalDateTime lastExecutedAt;
    private LocalDateTime createdAt;
}
