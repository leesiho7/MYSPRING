package com.tem.spring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring AI Ollama(원격 Nosana GPU 노드) 호출용 HTTP 타임아웃 설정.
 *
 * 원격 GPU 노드는 첫 추론 요청 시 모델(llama3)을 GPU 메모리에 로딩하는
 * 콜드스타트(수십 초)가 발생한다. 기본 read timeout이 짧으면 이 구간에서
 * 요청이 끊겨 예외가 발생하고, AiResearchChatService 가 조용히 하드코딩
 * 템플릿으로 폴백하여 "단순 챗봇처럼" 답하게 되는 원인이 된다.
 *
 * RestClientCustomizer 는 Spring AI 가 주입받는 RestClient.Builder 에도
 * 적용되므로, Ollama 호출의 read timeout 을 넉넉하게(180초) 확보한다.
 */
@Slf4j
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer ollamaTimeoutCustomizer() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(15))
                .withReadTimeout(Duration.ofSeconds(180));

        log.info("[HttpClientConfig] RestClient timeout 적용 (connect=15s, read=180s) - 원격 GPU 콜드스타트 대비");
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(settings));
    }

    @Bean
    public org.springframework.boot.web.reactive.function.client.WebClientCustomizer ollamaWebClientCustomizer() {
        return builder -> {
            reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(180))
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000);
            builder.clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient));
        };
    }
}
