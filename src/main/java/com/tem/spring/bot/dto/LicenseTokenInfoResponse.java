package com.tem.spring.bot.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 라이선스 토큰 정보 및 텔레그램 연동 상태 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LicenseTokenInfoResponse {

    private boolean success;
    private String message;

    private Long tokenId;
    private String tokenString;
    private Long userId;
    private String username;
    private String paymentTxHash;
    private String paymentNetwork;
    private double amountUsdt;

    private boolean isActive;
    private String telegramChatId;
    private boolean telegramLinked;
    private String telegramDeepLink;

    private Long assignedInstanceId;
    private String containerId;

    private LocalDateTime startDate;
    private LocalDateTime expiredAt;
    private long remainingDays;
}
