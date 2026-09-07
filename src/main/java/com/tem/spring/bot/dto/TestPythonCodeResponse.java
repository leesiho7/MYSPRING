package com.tem.spring.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestPythonCodeResponse {

    private boolean valid;
    private String status; // PASSED, SYNTAX_ERROR, SECURITY_VIOLATION
    private String message;
    private List<String> detectedLibraries;
    private String simulatedOutput;
    private double simulatedWinRate;
    private double simulatedPnlPct;
    private int totalBars;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double maxDrawdownPct;
    private double sharpeRatio;
    private double profitFactor;
}
