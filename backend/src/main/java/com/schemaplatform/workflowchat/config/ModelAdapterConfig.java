package com.schemaplatform.workflowchat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemaplatform.workflowchat.runtime.ModelAdapter;
import com.schemaplatform.workflowchat.runtime.ModelRestAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ModelAdapterConfig {
  @Bean ModelAdapter modelAdapter(RestClient client, RuntimeProperties props, ObjectMapper mapper) {
    return new ModelRestAdapter(client, props, mapper);
  }
}
