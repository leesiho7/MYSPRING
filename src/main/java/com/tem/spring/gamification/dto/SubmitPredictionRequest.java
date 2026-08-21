package com.tem.spring.gamification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitPredictionRequest {

    @NotNull(message = "유저 ID가 필요합니다.")
    private Long userId;

    @NotBlank(message = "종목 코드가 필요합니다.")
    private String symbol; // BTCUSDT, NVDA

    @NotBlank(message = "예측 유형을 선택하세요. (DIRECTION_24H 또는 PRICE_SNIPER)")
    private String predictionType; // DIRECTION_24H, PRICE_SNIPER

    private String predictedDirection; // BULL, BEAR

    private Double predictedPrice; // 스나이퍼 모드 시 목표가 (선택)
}
