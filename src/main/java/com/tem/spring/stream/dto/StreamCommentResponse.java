package com.tem.spring.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamCommentResponse {

    private Long commentId;
    private Long channelId;
    private Long userId;
    private String authorNickname;
    private String content;
    private String sentimentBias;
    private LocalDateTime createdAt;
}
