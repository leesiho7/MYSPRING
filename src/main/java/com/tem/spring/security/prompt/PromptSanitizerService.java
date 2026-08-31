package com.tem.spring.security.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * [프롬프트 & RAG 데이터 탈취 방지 (Prompt Sanitization Pipeline)]
 * 1. VectorStore 검색된 컨텍스트 및 유저 입력 텍스트 내 악의적 프롬프트 인젝션 키워드 사전 차단/무력화
 * 2. 시스템 프롬프트 탈취, 역할 탈취(Jailbreak), 구분자 오염(Delimiter Collision) 정규식 정제
 * 3. RAG 컨텍스트의 엄격한 길이 제한(Hard Truncation) 및 격리 태그(<context>...</context>) 패키징
 */
@Slf4j
@Service
public class PromptSanitizerService {

    // 차단 및 무력화 대상 프롬프트 인젝션 정규식 패턴 목록
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // Direct / Indirect Instruction Override
            Pattern.compile("(?i)(ignore|disregard|forget|override)\\s+(all\\s+)?(previous\\s+|prior\\s+|above\\s+|system\\s+)?(instructions|directives|prompts|rules|commands|guidelines)"),
            Pattern.compile("(?i)(print|output|display|show|reveal|leak|repeat|dump)\\s+(the\\s+)?(system\\s+prompt|initial\\s+prompt|developer\\s+message|hidden\\s+instructions)"),
            Pattern.compile("(?i)\\[system\\s*prompt\\]"),
            Pattern.compile("(?i)\\[instruction\\]"),
            Pattern.compile("(?i)<\\|im_start\\|>|<\\|im_end\\|>|<\\|system\\|>|<\\|user\\|>|<\\|assistant\\|>"),
            Pattern.compile("(?i)<<SYS>>|<</SYS>>|\\[INST\\]|\\[/INST\\]"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(in\\s+)?(DAN(\\s+mode)?|jailbreak(en)?|unrestricted|developer\\s+mode|a\\s+new\\s+ai)"),
            Pattern.compile("(?i)(DAN\\s+mode|jailbreak|jailbroken|unrestricted\\s+mode|godmode)"),
            Pattern.compile("(?i)system\\s+override\\s*[:=]"),
            Pattern.compile("(?i)act\\s+as\\s+(an?\\s+)?(unrestricted|malicious|godmode|DAN)"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?safety"),
            Pattern.compile("(?i)new\\s+rule\\s*:\\s*ignore")
    );

    // 태그 탈취 방지를 위한 위험한 XML/HTML 마크업 태그 패턴
    private static final Pattern DELIMITER_TAGS = Pattern.compile("(?i)</?(context|rag_context|system|instruction|prompt|rules)>");

    // 기본 단일 문서 및 전체 컨텍스트 최대 길이 제한
    private static final int DEFAULT_MAX_USER_PROMPT_LEN = 1000;
    private static final int DEFAULT_MAX_RAG_CONTEXT_LEN = 2500;
    private static final int DEFAULT_MAX_SINGLE_SNIPPET_LEN = 600;

    /**
     * 유저 입력 프롬프트를 검증 및 정제합니다.
     */
    public String sanitizeUserPrompt(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.isBlank()) {
            return "";
        }

        String cleaned = rawPrompt.trim();

        // 1. 길이 하드 캡 (Hard Truncation)
        if (cleaned.length() > DEFAULT_MAX_USER_PROMPT_LEN) {
            log.warn("[PromptSanitizer] ⚠️ User prompt exceeded max length ({} chars). Truncated to {}.",
                    cleaned.length(), DEFAULT_MAX_USER_PROMPT_LEN);
            cleaned = cleaned.substring(0, DEFAULT_MAX_USER_PROMPT_LEN);
        }

        // 2. 인젝션 패턴 검사 및 무력화
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(cleaned).find()) {
                log.warn("[PromptSanitizer] 🚨 Potential Prompt Injection detected in User Prompt! Pattern: '{}'. Neutralizing...", pattern.pattern());
                cleaned = pattern.matcher(cleaned).replaceAll("[PROTECTED_DIRECTIVE_BLOCKED]");
            }
        }

        // 3. 컨텍스트 구분자 태그 제거 (태그 탈출 방지)
        cleaned = DELIMITER_TAGS.matcher(cleaned).replaceAll("");

        return cleaned;
    }

    /**
     * RAG (VectorStore / 웹 스크래핑) 뉴스 컨텍스트 목록을 안전하게 정제 및 Truncation합니다.
     */
    public List<String> sanitizeRagSnippets(List<String> rawSnippets) {
        if (rawSnippets == null || rawSnippets.isEmpty()) {
            return List.of();
        }

        List<String> sanitizedList = new ArrayList<>();
        int currentTotalLength = 0;

        for (String snippet : rawSnippets) {
            if (snippet == null || snippet.isBlank()) continue;

            String cleaned = snippet.trim();

            // 1. 개별 스니펫 길이 제한
            if (cleaned.length() > DEFAULT_MAX_SINGLE_SNIPPET_LEN) {
                cleaned = cleaned.substring(0, DEFAULT_MAX_SINGLE_SNIPPET_LEN) + "...";
            }

            // 2. 외부 웹 데이터 내 간접 프롬프트 인젝션(Indirect Prompt Injection) 제거
            for (Pattern pattern : INJECTION_PATTERNS) {
                if (pattern.matcher(cleaned).find()) {
                    log.warn("[PromptSanitizer] 🚨 Indirect Prompt Injection filtered out from RAG snippet: '{}'", pattern.pattern());
                    cleaned = pattern.matcher(cleaned).replaceAll("[UNTRUSTED_CONTENT_STRIPPED]");
                }
            }

            // 3. Delimiter 태그 치환
            cleaned = DELIMITER_TAGS.matcher(cleaned).replaceAll("");

            // 4. 전체 컨텍스트 누적 길이 제한 (Rule 2. RAG Context Strict Truncation)
            if (currentTotalLength + cleaned.length() > DEFAULT_MAX_RAG_CONTEXT_LEN) {
                int remainingAllowed = Math.max(0, DEFAULT_MAX_RAG_CONTEXT_LEN - currentTotalLength);
                if (remainingAllowed > 50) {
                    sanitizedList.add(cleaned.substring(0, remainingAllowed) + " [TRUNCATED]");
                }
                log.info("[PromptSanitizer] 🛑 RAG context reached hard cap of {} chars. Remaining items omitted.", DEFAULT_MAX_RAG_CONTEXT_LEN);
                break;
            }

            sanitizedList.add(cleaned);
            currentTotalLength += cleaned.length();
        }

        return sanitizedList;
    }

    /**
     * RAG 컨텍스트를 System Prompt와 완벽히 격리된 User Message 영역에 주입하기 위해
     * 명확한 경계 구분자(<context>...</context>)로 감싸 안전한 문자열 블록을 생성합니다.
     */
    public String buildIsolatedContextBlock(String symbol, List<String> sanitizedSnippets) {
        if (sanitizedSnippets == null || sanitizedSnippets.isEmpty()) {
            return "<context>\n(검색된 최신 시장 컨텍스트 없음)\n</context>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<context>\n");
        sb.append("<!-- RAG GROUND-TRUTH MARKET DATA: ").append(symbol != null ? symbol : "UNKNOWN").append(" -->\n");
        for (int i = 0; i < sanitizedSnippets.size(); i++) {
            sb.append(String.format("[%d] %s\n", i + 1, sanitizedSnippets.get(i)));
        }
        sb.append("</context>");
        return sb.toString();
    }

    private static final Pattern HTML_SCRIPT_PATTERN = Pattern.compile("(?i)<script.*?>.*?</script>|<style.*?>.*?</style>|<[^>]+>|javascript:|eval\\s*\\(");

    /**
     * RAG 백도어 및 악의적 임베딩 오염 검사 (Vector Poisoning Rule)
     * - VectorStore(ChromaDB)에 인덱싱되기 전 프롬프트 조작/탈취 키워드가 포함되어 있는지 검사합니다.
     */
    public boolean isVectorPoisoned(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(content).find()) {
                log.warn("[PromptSanitizer] ☣️ Vector Poisoning attack payload detected! Pattern: '{}'", pattern.pattern());
                return true;
            }
        }

        if (HTML_SCRIPT_PATTERN.matcher(content).find()) {
            log.warn("[PromptSanitizer] ☣️ Vector Poisoning HTML/Script payload detected!");
            return true;
        }

        return false;
    }

    /**
     * VectorStore 인덱싱을 위한 순수 텍스트 정제 (HTML 태그, 스크립트 코드 제거)
     */
    public String cleanForVectorStore(String content) {
        if (content == null) return "";
        String cleaned = HTML_SCRIPT_PATTERN.matcher(content).replaceAll(" ");
        cleaned = DELIMITER_TAGS.matcher(cleaned).replaceAll(" ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }
}
