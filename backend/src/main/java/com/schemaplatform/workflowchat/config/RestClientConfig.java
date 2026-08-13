package com.schemaplatform.workflowchat.config;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient 配置，供 RuntimeRestAdapter 使用。
 * 超时由 RuntimeProperties 驱动。
 */
@Configuration
public class RestClientConfig {

  @Bean
  public RestClient runtimeRestClient(RuntimeProperties props) {
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
        .withConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
        .withReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
    ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(SimpleClientHttpRequestFactory.class, settings);
    return RestClient.builder()
        .baseUrl(props.baseUrl())
        .requestFactory(factory)
        .build();
  }
}
