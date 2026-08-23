package com.tem.spring.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;

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

    @JsonCreator
    public static TimeFrame fromString(String val) {
        if (val == null || val.isBlank()) return D1;
        String normalized = val.trim().toUpperCase();
        if (normalized.equals("4H") || normalized.equals("4h")) return H4;
        if (normalized.equals("1H") || normalized.equals("1h")) return H1;
        if (normalized.equals("1D") || normalized.equals("1d")) return D1;
        if (normalized.equals("1M") || normalized.equals("1m")) return M1;
        if (normalized.equals("5M") || normalized.equals("5m")) return M5;
        if (normalized.equals("15M") || normalized.equals("15m")) return M15;

        for (TimeFrame tf : values()) {
            if (tf.name().equalsIgnoreCase(normalized) || tf.code.equalsIgnoreCase(normalized)) {
                return tf;
            }
        }
        return D1;
    }
}

