package com.tem.spring.community.dto;

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
public class CreatePostRequest {

    @NotNull(message = "작성자 ID가 필요합니다.")
    private Long authorId;

    @NotBlank(message = "종목 코드를 입력해주세요.")
    private String symbol; // 예: BTCUSDT

    @NotBlank(message = "제목을 입력해주세요.")
    private String title;

    @NotBlank(message = "분석 내용을 입력해주세요.")
    private String content;

    private String sentimentBias; // BULLISH, BEARISH, NEUTRAL
    private double targetPrice;
    private String backtestSnapshot; // 선택: 내가 돌린 백테스트 요약 결과
}
