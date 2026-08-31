package com.tem.spring.security.masking;

/**
 * 민감 데이터 마스킹 유틸리티
 */
public final class SecurityMaskingUtil {

    private SecurityMaskingUtil() {}

    public static String maskToken(String token) {
        if (token == null || token.isBlank()) return "***";
        if (token.length() <= 8) return "***";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    public static String maskPrivateKey(String key) {
        if (key == null || key.isBlank()) return "***";
        if (key.length() <= 10) return "***";
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }
}
