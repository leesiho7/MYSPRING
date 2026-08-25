package com.tem.spring.bot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestPythonCodeRequest {

    @NotBlank(message = "파이썬 코드는 필수입니다.")
    private String pythonCode;

    @Builder.Default
    private String symbol = "BTCUSDT";

    @Builder.Default
    private String timeFrame = "5m";
}
