package com.tem.spring.core.model;

public enum ActionType {
    STRONG_BUY("강한 매수"),
    BUY("매수"),
    HOLD("관망 / 중립"),
    SELL("매도"),
    STRONG_SELL("강한 매도");

    private final String description;

    ActionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
