package com.tem.spring.gamification.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 관리자 전용 에스크로 풀 설정 변경 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminEscrowConfigRequest {

    @NotNull(message = "초기 예치 풀 용량은 필수입니다.")
    @Min(value = 0, message = "풀 용량은 0 이상이어야 합니다.")
    private Double initialCapacity;

    @Builder.Default
    private String status = "ACTIVE";

    private String escrowAddress;

    private String network;
}