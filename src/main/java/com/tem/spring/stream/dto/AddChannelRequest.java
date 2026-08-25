package com.tem.spring.stream.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddChannelRequest {

    @NotBlank(message = "채널 이름은 필수입니다.")
    private String channelName;

    @NotBlank(message = "방송 제목은 필수입니다.")
    private String streamTitle;

    @Builder.Default
    private String streamType = "LIVE_STREAM";

    @Builder.Default
    private String platform = "YOUTUBE";

    @NotBlank(message = "임베드 URL 또는 동영상 ID는 필수입니다.")
    private String embedUrl;

    @Builder.Default
    private String category = "CRYPTO";

    private String thumbnailUrl;
    private String streamerName;

    @Builder.Default
    private String targetSymbol = "BTCUSDT";

    @Builder.Default
    private String liveSentiment = "BULLISH";
}
