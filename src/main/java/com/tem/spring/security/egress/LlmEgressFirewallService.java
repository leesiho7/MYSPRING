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

    // ---------------------------------------------------------------------
    // [보안 하드 룰 ①] AI 출력 Sanitization (Egress Output Reverse-Masking)
    // ---------------------------------------------------------------------
    private static final java.util.List<java.util.Map.Entry<Pattern, String>> OUTPUT_OBFUSCATION_MAP = java.util.List.of(
            java.util.Map.entry(Pattern.compile("(?i)\\bta4j\\b"), "AETHER Institutional Quant Matrix"),
            java.util.Map.entry(Pattern.compile("(?i)\\bfastdtw\\b"), "AETHER 시계열 빅데이터 프랙탈 엔진"),
            java.util.Map.entry(Pattern.compile("(?i)\\bchromadb\\b"), "글로벌 금융 인텔리전스 데이터웨어하우스"),
            java.util.Map.entry(Pattern.compile("(?i)\\bbge-?m3\\b"), "AETHER Multi-vector Semantic Core"),
            java.util.Map.entry(Pattern.compile("(?i)\\bollama\\b"), "AETHER Autonomous On-Premise GPU Engine"),
            java.util.Map.entry(Pattern.compile("(?i)\\bqwen(-?max)?\\b"), "AETHER Deep Reasoning Core"),
            java.util.Map.entry(Pattern.compile("(?i)\\blangchain\\b"), "AETHER Enterprise Pipeline"),
            java.util.Map.entry(Pattern.compile("(?i)\\bspring\\s*ai\\b"), "AETHER Intelligence Platform")
    );

    /**
     * AI 응답 내 언어 표류(Language Drift)를 검사하여,
     * 타겟 언어(ko, en, zh/cn)에 맞지 않는 텍스트가 대량 검출될 경우 안전한 기관급 폴백 메시지로 정화합니다.
     */
    public String validateAndFixLanguage(String rawOutput, String targetLang) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return rawOutput;
        }

        String lang = (targetLang != null && !targetLang.isBlank()) ? targetLang.toLowerCase() : "ko";

        // 중국어 유니코드 한자 범위(\u4E00-\u9FA5) 카운트
        long chineseCharCount = rawOutput.chars()
                .filter(ch -> ch >= 0x4E00 && ch <= 0x9FA5)
                .count();

        // 한글 유니코드 음절 범위(\uAC00-\uD7A3) 카운트
        long koreanCharCount = rawOutput.chars()
                .filter(ch -> ch >= 0xAC00 && ch <= 0xD7A3)
                .count();

        if ("ko".equals(lang)) {
            // 한국어 모드인데 중국어가 5글자 이상 감지된 경우
            if (chineseCharCount > 5) {
                log.warn("[LlmEgressFirewall] 🚨 Qwen Language Drift to Chinese detected in KO mode! (count: {})", chineseCharCount);
                return "AETHER 기관급 인텔리전스 엔진의 실시간 다국어 동기화 세션이 정화되었습니다. 검증된 AETHER Institutional Quant Matrix 기반 정량 분석 결과로 대체 제공합니다.";
            }
        } else if ("zh".equals(lang) || "cn".equals(lang)) {
            // 중국어 모드인데 한글이 5글자 이상 감지된 경우
            if (koreanCharCount > 5) {
                log.warn("[LlmEgressFirewall] 🚨 Language Drift to Korean detected in ZH mode! (count: {})", koreanCharCount);
                return "AETHER 机构级量化情报引擎的实时多语言会话已校准。现提供基于 AETHER Institutional Quant Matrix 的精准量化分析报告。";
            }
        } else if ("en".equals(lang)) {
            // 영어 모드인데 중국어나 한글이 5글자 이상 감지된 경우
            if (chineseCharCount > 5 || koreanCharCount > 5) {
                log.warn("[LlmEgressFirewall] 🚨 Language Drift detected in EN mode! (zh: {}, ko: {})", chineseCharCount, koreanCharCount);
                return "AETHER Institutional Intelligence Engine multilingual session synchronized. Providing calibrated quantitative analysis based on AETHER Institutional Quant Matrix.";
            }
        }

        return rawOutput;
    }

    public String validateAndFixLanguage(String rawOutput) {
        return validateAndFixLanguage(rawOutput, "ko");
    }

    public String sanitizeLlmOutput(String rawOutput) {
        return sanitizeLlmOutput(rawOutput, "ko");
    }

    /**
     * AI의 최종 응답 텍스트(Output)에서 타겟 언어 표류를 차단하고 내부 오픈소스/인프라 명칭을 브랜드 명칭으로 치환합니다.
     */
    public String sanitizeLlmOutput(String rawOutput, String targetLang) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return rawOutput;
        }

        // 1. 타겟 언어 표류(Language Drift) 1차 검증 및 정화
        String validated = validateAndFixLanguage(rawOutput, targetLang);

        String sanitized = validated;
        boolean replaced = false;

        for (var entry : OUTPUT_OBFUSCATION_MAP) {
            Matcher m = entry.getKey().matcher(sanitized);
            if (m.find()) {
                sanitized = m.replaceAll(entry.getValue());
                replaced = true;
            }
        }

        if (replaced) {
            log.info("[LlmEgressFirewall] 🛡️ Successfully obfuscated internal open-source tech names into Institutional Brand in LLM output");
        }

        return sanitized;
    }
}
