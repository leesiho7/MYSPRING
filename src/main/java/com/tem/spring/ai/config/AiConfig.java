package com.tem.spring.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * 인메모리 SimpleVectorStore 및 BGE-M3 로컬 임베딩 연동 설정
 * 불필요한 외부 ChromaDB 네트워크 연결 시도를 완전히 차단하고 고성능 인메모리 벡터 검색을 수행
 */
@Slf4j
@Configuration
public class AiConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.embedding.options.model:bge-m3:latest}")
    private String embeddingModelName;

    @Value("${spring.ai.ollama.chat.options.model:qwen2.5:14b}")
    private String chatModelName;

    @Bean
    @Primary
    public org.springframework.ai.chat.model.ChatModel chatModel(ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                                                  ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        try {
            RestClient.Builder restBuilder = restClientBuilderProvider.orderedStream().findFirst().orElseGet(RestClient::builder);
            WebClient.Builder webBuilder = webClientBuilderProvider.orderedStream().findFirst().orElseGet(WebClient::builder);
            org.springframework.ai.ollama.api.OllamaApi ollamaApi = new org.springframework.ai.ollama.api.OllamaApi(ollamaBaseUrl, restBuilder, webBuilder);
            org.springframework.ai.ollama.api.OllamaOptions options = org.springframework.ai.ollama.api.OllamaOptions.builder()
                    .withModel(chatModelName)
                    .withTemperature(0.2)
                    .build();
            log.info("[AiConfig] ✅ Initialized dedicated OllamaChatModel with model: '{}' at {}", chatModelName, ollamaBaseUrl);
            return new org.springframework.ai.ollama.OllamaChatModel(ollamaApi, options);
        } catch (Throwable t) {
            log.error("[AiConfig] ❌ Failed to init OllamaChatModel: {}", t.getMessage(), t);
            return null;
        }
    }

    @Bean
    @Primary
    public org.springframework.ai.chat.client.ChatClient chatClient(org.springframework.ai.chat.model.ChatModel chatModel) {
        if (chatModel != null) {
            log.info("[AiConfig] ✅ Initialized dedicated ChatClient from ChatModel");
            return org.springframework.ai.chat.client.ChatClient.create(chatModel);
        }
        return null;
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                         ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        try {
            RestClient.Builder restBuilder = restClientBuilderProvider.orderedStream().findFirst().orElseGet(RestClient::builder);
            WebClient.Builder webBuilder = webClientBuilderProvider.orderedStream().findFirst().orElseGet(WebClient::builder);
            org.springframework.ai.ollama.api.OllamaApi ollamaApi = new org.springframework.ai.ollama.api.OllamaApi(ollamaBaseUrl, restBuilder, webBuilder);
            org.springframework.ai.ollama.api.OllamaOptions options = org.springframework.ai.ollama.api.OllamaOptions.builder()
                    .withModel(embeddingModelName)
                    .build();
            log.info("[AiConfig] ✅ Initialized dedicated OllamaEmbeddingModel with BGE-M3 model: '{}' at {}", embeddingModelName, ollamaBaseUrl);
            return new org.springframework.ai.ollama.OllamaEmbeddingModel(ollamaApi, options);
        } catch (Throwable t) {
            log.warn("[AiConfig] Failed to init OllamaEmbeddingModel, using fallback: {}", t.getMessage());
            return createFallbackEmbeddingModel();
        }
    }

    private EmbeddingModel createFallbackEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public org.springframework.ai.embedding.EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                List<org.springframework.ai.embedding.Embedding> embeddings = request.getInstructions().stream()
                        .map(text -> new org.springframework.ai.embedding.Embedding(generateTextEmbedding(text), 0))
                        .toList();
                return new org.springframework.ai.embedding.EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(org.springframework.ai.document.Document document) {
                return generateTextEmbedding(document.getContent());
            }

            private float[] generateTextEmbedding(String text) {
                float[] vec = new float[1024]; // BGE-M3 1024-dimension standard
                if (text != null) {
                    byte[] bytes = text.getBytes();
                    for (int i = 0; i < bytes.length; i++) {
                        vec[i % 1024] += (float) (bytes[i] & 0xFF) / 255.0f;
                    }
                }
                return vec;
            }
        };
    }

    @Bean
    @Primary
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.info("[AiConfig] ✅ Initialized in-memory SimpleVectorStore (Zero-ChromaDB dependency)");
        return new SimpleVectorStore(embeddingModel);
    }
}
