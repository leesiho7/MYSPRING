package com.tem.spring.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 가상 인스턴스 봇의 24시간 실시간 터미널 stdout 로그 엔티티
 */
@Entity
@Table(name = "bot_execution_logs", indexes = {
        @Index(name = "idx_bot_log_instance", columnList = "instanceId"),
        @Index(name = "idx_bot_log_time", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotExecutionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long instanceId;

    @Column(nullable = false, length = 15)
    @Builder.Default
    private String logLevel = "INFO"; // INFO, ORDER, SIGNAL, WARN, ERROR

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
