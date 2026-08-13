package com.schemaplatform.workflowchat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemaplatform.workflowchat.runtime.ModelAdapter;
import com.schemaplatform.workflowchat.runtime.ModelMockAdapter;
import com.schemaplatform.workflowchat.runtime.ModelRestAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ModelAdapterConfig {
  private static final Logger log = LoggerFactory.getLogger(ModelAdapterConfig.class);

  @Bean
  ModelAdapter modelAdapter(RestClient client, RuntimeProperties props, ObjectMapper mapper) {
    if (props.useMock()) {
      log.warn("ModelAdapter 使用 MOCK（RUNTIME_MOCK_ENABLED=true）");
      return new ModelMockAdapter();
    }
    return new ModelRestAdapter(client, props, mapper);
  }
}
