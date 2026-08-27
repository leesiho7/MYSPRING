package com.tem.spring.bot.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 입금 처리 완료 및 라이선스 토큰 발급 결과 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositProcessingResultDto {

    private boolean success;
    private String message;

    // 발급된 SHA-256 라이선스 토큰
    private String licenseToken;

    private Long userId;
    private String username;
    private String depositAddress;
    private String txHash;
    private String network;
    private double amountUsdt;

    // 활성화된 봇 인스턴스 정보
    private Long instanceId;
    private String botName;
    private String containerId;
    private String instanceStatus;

    // 공식 텔레그램 봇 1:1 딥링크 URL (클릭 시 원클릭 연동)
    private String telegramDeepLink;
    private String telegramBotUsername;
    private boolean telegramLinked;

    private LocalDateTime startDate;
    private LocalDateTime expiredAt;
    private long remainingDays;
}
