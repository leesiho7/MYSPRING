package com.tem.spring.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Cryptomus API 서명(Sign) 생성 및 검증 유틸리티
 * 서명 생성 규칙: MD5( Base64( JSON_DATA ) + API_KEY )
 */
@Slf4j
@Component
public class CryptomusSignatureUtil {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 요청 JSON 데이터와 API Key를 기반으로 Cryptomus Sign 생성
     */
    public String generateSign(Object requestPayload, String apiKey) {
        try {
            String jsonString = objectMapper.writeValueAsString(requestPayload);
            String base64Encoded = Base64.getEncoder().encodeToString(jsonString.getBytes(StandardCharsets.UTF_8));
            String raw = base64Encoded + apiKey;

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[CryptomusSignatureUtil] Failed to generate sign", e);
            return "";
        }
    }

    /**
     * 웹훅 요청의 Sign 검증
     */
    public boolean verifyWebhookSign(String rawJson, String receivedSign, String apiKey) {
        if (receivedSign == null || apiKey == null) return false;
        try {
            String base64Encoded = Base64.getEncoder().encodeToString(rawJson.getBytes(StandardCharsets.UTF_8));
            String raw = base64Encoded + apiKey;

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().equalsIgnoreCase(receivedSign);
        } catch (Exception e) {
            log.error("[CryptomusSignatureUtil] Failed to verify webhook sign", e);
            return false;
        }
    }
}
