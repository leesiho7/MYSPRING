package com.tem.spring.gamification.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 에스크로 출금/지급 및 회수 감사 원장 항목 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminEscrowAuditLogDto {
    private String type;
    private String description;
    private double amount;
    private String destinationAddress;
    private String network;
    private String txHash;
    private String status;
    private LocalDateTime timestamp;
}