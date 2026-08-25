package com.tem.spring.stream.dto;

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
public class CreateStreamCommentRequest {

    @NotNull(message = "유저 ID는 필수입니다.")
    private Long userId;

    @NotBlank(message = "댓글 내용은 필수입니다.")
    private String content;

    @Builder.Default
    private String sentimentBias = "BULLISH"; // BULLISH, BEARISH, NEUTRAL
}
