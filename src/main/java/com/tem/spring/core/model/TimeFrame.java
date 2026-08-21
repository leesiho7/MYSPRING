package com.tem.spring.core.model;

public enum TimeFrame {
    M1("1m"),
    M5("5m"),
    M15("15m"),
    H1("1h"),
    H4("4h"),
    D1("1d");

    private final String code;

    TimeFrame(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
