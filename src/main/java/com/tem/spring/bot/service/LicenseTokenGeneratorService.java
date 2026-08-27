package com.tem.spring.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 2단계: SHA-256 라이선스 토큰 자동 생성 서비스
 * 알고리즘: Token = SHA-256(User_ID + Payment_TxHash + Timestamp + Secret_Key)
 */
@Slf4j
@Service
public class LicenseTokenGeneratorService {

    @Value("${blockchain.license.secret-key:aether_trading_ai_super_secret_license_salt_999}")
    private String secretKey;

    /**
     * SHA-256 라이선스 토큰 생성
     */
    public String generateLicenseToken(Long userId, String txHash, long timestamp) {
        String rawInput = String.format("USER_%d_TX_%s_TS_%d_SEC_%s", userId, txHash, timestamp, secretKey);
        return sha256Hex(rawInput);
    }

    /**
     * SHA-256 해시 연산
     */
    public String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("[LicenseTokenGenerator] SHA-256 algorithm not available", e);
            throw new RuntimeException("SHA-256 암호화 알고리즘 오류", e);
        }
    }
}
