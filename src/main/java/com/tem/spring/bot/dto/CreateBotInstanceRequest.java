package com.tem.spring.bot.dto;

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
public class CreateBotInstanceRequest {

    @NotNull(message = "유저 ID는 필수입니다.")
    private Long userId;

    @NotBlank(message = "봇 이름은 필수입니다.")
    private String botName;

    @Builder.Default
    private String mode = "BEGINNER"; // BEGINNER, DEVELOPER

    @Builder.Default
    private String exchange = "BINANCE"; // BINANCE, BYBIT, UPBIT

    @Builder.Default
    private String symbol = "BTCUSDT";

    @Builder.Default
    private String timeFrame = "5m"; // 1m, 5m, 15m, 1h, 1d

    private String apiKey;
    private String apiSecret;

    // 초보자 모드 개별 모듈 토글 및 파라미터
    @Builder.Default
    private boolean useGoldmanRiskGuard = true; // 골드만삭스 리스크 세이프가드 (ATR 변동성 감응형 비중 축소)
    @Builder.Default
    private double maxDailyLossPct = 4.0; // 1일 최대 허용 손실 (도달 시 당일 매매 셧다운)
    @Builder.Default
    private double volatilitySensitivity = 1.0; // 변동성 감응 계수 (0.5 ~ 2.0)

    @Builder.Default
    private boolean useRsi = true; // RSI 모멘텀 오실레이터 토글
    @Builder.Default
    private double rsiBuyThreshold = 30.0;
    @Builder.Default
    private double rsiSellThreshold = 70.0;

    @Builder.Default
    private boolean useSma = true; // 이동평균 크로스오버 토글
    @Builder.Default
    private int smaShortPeriod = 20;
    @Builder.Default
    private int smaLongPeriod = 50;

    @Builder.Default
    private boolean useTakeProfitStopLoss = true; // 손익비 및 포지션 관리 토글
    @Builder.Default
    private double takeProfitPct = 6.0;
    @Builder.Default
    private double stopLossPct = 3.0;
    @Builder.Default
    private double positionSizePct = 20.0; // 1회 진입 비중 %

    // 개발자 모드: 파이썬 코드
    private String pythonCode;
}
