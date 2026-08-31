package com.tem.spring.security.masking;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [2중 API Key 및 토큰 격리 파이프라인 (Secrets Isolation - Logback Safety Converter)]
 * Qwen-Max, DashScope, Bright Data, Telegram, Web3 Private Key, Escrow Secret, DB Password 등
 * 민감한 정보가 로그 파일이나 콘솔에 평문으로 남지 않도록 완벽히 마스킹(***) 처리합니다.
 */
public class SensitiveDataMaskingConverter extends ClassicConverter {

    private static final List<MaskRule> MASK_RULES = List.of(
            // 1. OpenAI / Qwen / DashScope / Cloud API Keys (sk-..., sk_...)
            new MaskRule(Pattern.compile("(?i)(sk[-_][a-zA-Z0-9]{8,})"), "$1", 4, 3),

            // 2. Bearer Authorization Tokens
            new MaskRule(Pattern.compile("(?i)(Bearer\\s+)([a-zA-Z0-9._\\-]{15,})"), "$2", 3, 3),

            // 3. Web3 Ethereum/Polygon/BSC 64-char Hex Private Keys (0x... or plain 64 hex)
            new MaskRule(Pattern.compile("(?i)(0x[a-fA-F0-9]{64})"), "$1", 4, 4),
            new MaskRule(Pattern.compile("(?i)(private[-_]?key\\s*[:=]\\s*)([a-fA-F0-9]{32,64})"), "$2", 3, 3),

            // 4. Telegram Bot Tokens (e.g. 123456789:ABCdefGHIjklMNOpqrsTUVwxyz)
            new MaskRule(Pattern.compile("(\\d{8,11}:[a-zA-Z0-9_-]{30,40})"), "$1", 4, 4),

            // 5. Bright Data & Generic UUID API Keys (e.g. 4a62ad76-a8e4-46cb-9cb0-deaf9e6587a7)
            new MaskRule(Pattern.compile("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"), "$1", 4, 4),

            // 6. Key-Value pairs in JSON or Query Strings (apiKey, secret, password, token)
            new MaskRule(Pattern.compile("(?i)(api[-_]?key|password|secret|token|webhook[-_]?secret|private[-_]?key)(\"\\s*:\\s*\"|\\s*=\\s*\"?)([^\"\\s,;&]+)"), "$3", 2, 2)
    );

    @Override
    public String convert(ILoggingEvent event) {
        String originalMessage = event.getFormattedMessage();
        if (originalMessage == null || originalMessage.isEmpty()) {
            return originalMessage;
        }

        String masked = originalMessage;
        for (MaskRule rule : MASK_RULES) {
            masked = rule.applyMask(masked);
        }

        return masked;
    }

    private static class MaskRule {
        private final Pattern pattern;
        private final String targetGroup;
        private final int prefixKeep;
        private final int suffixKeep;

        public MaskRule(Pattern pattern, String targetGroup, int prefixKeep, int suffixKeep) {
            this.pattern = pattern;
            this.targetGroup = targetGroup;
            this.prefixKeep = prefixKeep;
            this.suffixKeep = suffixKeep;
        }

        public String applyMask(String input) {
            Matcher matcher = pattern.matcher(input);
            if (!matcher.find()) {
                return input;
            }

            StringBuilder sb = new StringBuilder();
            int lastIndex = 0;
            matcher.reset();

            while (matcher.find()) {
                sb.append(input, lastIndex, matcher.start());
                String matchedStr = matcher.group();
                String maskedValue = maskString(matchedStr, prefixKeep, suffixKeep);
                sb.append(maskedValue);
                lastIndex = matcher.end();
            }
            sb.append(input.substring(lastIndex));
            return sb.toString();
        }

        private String maskString(String str, int prefix, int suffix) {
            if (str == null || str.length() <= prefix + suffix + 2) {
                return "***";
            }
            String start = str.substring(0, prefix);
            String end = str.substring(str.length() - suffix);
            return start + "****" + end;
        }
    }
}
