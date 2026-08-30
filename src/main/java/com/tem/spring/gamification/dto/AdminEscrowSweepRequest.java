package com.tem.spring.gamification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * 관리자 긴급/이벤트 종료 자금 회수(Sweep) 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminEscrowSweepRequest {

    @NotBlank(message = "회수받으실 지갑 주소는 필수입니다.")
    private String destinationAddress;

    private Double amount;

    @Builder.Default
    private String network = "polygon";

    private Long adminUserId;
}