package com.tem.spring.security.egress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM Egress Traffic Firewall (외부 유출 방지 Egress 필터)
 * - LLM(Qwen, Ollama, OpenAI) API로 전송되는 모든 User/System 프롬프트 페이로드에서
 *   서버 내부 IP, DB 접속정보, API Key, 개인키, 개인정보(이메일, 연락처)가 실수로 유출되는 것을 차단
 */
@Slf4j
@Service
public class LlmEgressFirewallService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b01[016789]-?\\d{3,4}-?\\d{4}\\b|\\b\\+\\d{1,3}[- ]?\\d{1,4}[- ]?\\d{3,4}[- ]?\\d{3,4}\\b");
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(sk-[a-zA-Z0-9]{20,}|bearer\\s+[a-zA-Z0-9._\\-]{20,}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("0x[a-fA-F0-9]{64}");
    private static final Pattern DB_URL_PATTERN = Pattern.compile("(?i)jdbc:[a-zA-Z0-9:/_?=&.\\-]+");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|secret)\\s*[:=]\\s*['\"]?([^'\"\\s,;]+)['\"]?");

    /**
     * 외부 LLM으로 전송할 프롬프트 텍스트를 검사하고 민감 정보를 [REDACTED_*]로 마스킹합니다.
     */
    public String sanitizeEgressTraffic(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.isBlank()) {
            return rawPrompt;
        }

        String sanitized = rawPrompt;
        boolean redacted = false;

        // 1. Private Key 마스킹
        Matcher pkMatcher = PRIVATE_KEY_PATTERN.matcher(sanitized);
        if (pkMatcher.find()) {
            sanitized = pkMatcher.replaceAll("[REDACTED_PRIVATE_KEY]");
            redacted = true;
        }

        // 2. API Key / Bearer 토큰 마스킹
        Matcher akMatcher = API_KEY_PATTERN.matcher(sanitized);
        if (akMatcher.find()) {
            sanitized = akMatcher.replaceAll("[REDACTED_API_KEY]");
            redacted = true;
        }

        // 3. DB JDBC URL 마스킹
        Matcher dbMatcher = DB_URL_PATTERN.matcher(sanitized);
        if (dbMatcher.find()) {
            sanitized = dbMatcher.replaceAll("[REDACTED_DB_URL]");
            redacted = true;
        }

        // 4. 비밀번호 패턴 마스킹
        Matcher pwdMatcher = PASSWORD_PATTERN.matcher(sanitized);
        if (pwdMatcher.find()) {
            sanitized = pwdMatcher.replaceAll("$1=[REDACTED_PASSWORD]");
            redacted = true;
        }

        // 5. 이메일 주소 마스킹
        Matcher emailMatcher = EMAIL_PATTERN.matcher(sanitized);
        if (emailMatcher.find()) {
            sanitized = emailMatcher.replaceAll("[REDACTED_EMAIL]");
            redacted = true;
        }

        // 6. IP 주소 마스킹
        Matcher ipMatcher = IP_PATTERN.matcher(sanitized);
        if (ipMatcher.find()) {
            sanitized = ipMatcher.replaceAll("[REDACTED_IP]");
            redacted = true;
        }

        // 7. 전화번호 마스킹
        Matcher phoneMatcher = PHONE_PATTERN.matcher(sanitized);
        if (phoneMatcher.find()) {
            sanitized = phoneMatcher.replaceAll("[REDACTED_PHONE]");
            redacted = true;
        }

        if (redacted) {
            log.warn("[LlmEgressFirewall] 🚨 Redacted sensitive secrets / PII from outbound LLM prompt payload");
        }

        return sanitized;
    }
}
