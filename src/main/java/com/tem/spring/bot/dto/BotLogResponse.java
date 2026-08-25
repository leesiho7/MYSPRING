package com.tem.spring.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotLogResponse {

    private Long instanceId;
    private String botName;
    private String status;
    private List<LogEntry> logs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogEntry {
        private Long id;
        private String logLevel;
        private String message;
        private LocalDateTime timestamp;
    }
}
