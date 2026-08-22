package com.tem.spring.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.ChromaApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * ChromaDB 및 로컬 LLM 연동 설정
 * 로컬 ChromaDB(localhost:8000) 구동 시 실제 ChromaVectorStore 연결,
 * ChromaDB 미구동 시에는 인메모리 SimpleVectorStore로 안전하게 자동 Fallback
 */
@Slf4j
@Configuration
public class AiConfig {

    @Value("${spring.ai.vectorstore.chroma.client.host:http://localhost}")
    private String chromaHost;

    @Value("${spring.ai.vectorstore.chroma.client.port:8000}")
    private int chromaPort;

    @Value("${spring.ai.vectorstore.chroma.collection-name:financial-market-news}")
    private String collectionName;


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
                float[] vec = new float[384];
                if (text != null) {
                    byte[] bytes = text.getBytes();
                    for (int i = 0; i < bytes.length; i++) {
                        vec[i % 384] += (float) (bytes[i] & 0xFF) / 255.0f;
                    }
                }
                return vec;
            }
        };
    }

    @Bean
    @Primary
    public VectorStore vectorStore(ObjectProvider<EmbeddingModel> embeddingModelProvider, ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        String chromaUrl = chromaHost + ":" + chromaPort;
        EmbeddingModel embeddingModel = embeddingModelProvider.orderedStream().findFirst().orElseGet(this::createFallbackEmbeddingModel);
        try {
            RestClient.Builder builder = restClientBuilderProvider.orderedStream().findFirst().orElseGet(RestClient::builder);
            // Quick probe to verify if ChromaDB server is actively running on port 8000
            RestClient probeClient = builder.baseUrl(chromaUrl).build();
            probeClient.get().uri("/api/v1/heartbeat").retrieve().toBodilessEntity();

            log.info("[AiConfig] ChromaDB is ONLINE at {}. Connecting ChromaVectorStore...", chromaUrl);
            ChromaApi chromaApi = new ChromaApi(chromaUrl, builder);
            return new ChromaVectorStore(embeddingModel, chromaApi, collectionName, true);
        } catch (Throwable e) {
            log.info("[AiConfig] ChromaDB (localhost:8000) is OFFLINE. Seamlessly operating with in-memory SimpleVectorStore (RAM-based, zero-config).");
            return new SimpleVectorStore(embeddingModel);
        }
    }
}
