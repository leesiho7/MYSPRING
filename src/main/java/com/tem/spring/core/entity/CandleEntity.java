package com.tem.spring.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

/**
 * 캔들(OHLCV) 시계열 데이터 저장을 위한 MySQL JPA 엔티티
 * (symbol + timestamp) 복합 유니크 인덱스로 멱등성 및 초고속 시계열 조회 보장
 */
@Entity
@Table(name = "candles", indexes = {
        @Index(name = "idx_symbol_timestamp", columnList = "symbol, timestamp", unique = true),
        @Index(name = "idx_symbol", columnList = "symbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private ZonedDateTime timestamp;

    @Column(nullable = false)
    private double open;

    @Column(nullable = false)
    private double high;

    @Column(nullable = false)
    private double low;

    @Column(nullable = false)
    private double close;

    @Column(nullable = false)
    private double volume;
}
