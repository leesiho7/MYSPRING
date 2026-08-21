package com.tem.spring.gamification.dto;

import com.tem.spring.quant.dto.CustomStrategyRequest;
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
public class SubmitArenaStrategyRequest {

    @NotNull(message = "작성자 ID가 필요합니다.")
    private Long authorId;

    @NotBlank(message = "전략명을 입력해주세요.")
    private String strategyName;

    @NotBlank(message = "종목을 입력해주세요.")
    private String symbol;

    @NotNull(message = "레고 전략 설정이 필요합니다.")
    private CustomStrategyRequest strategyConfig;
}
