package com.schemaplatform.workflowchat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemaplatform.workflowchat.runtime.RuntimeAdapter;
import com.schemaplatform.workflowchat.runtime.RuntimeAdapterSelector;
import com.schemaplatform.workflowchat.runtime.RuntimeUnavailableException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RuntimeAdapterConfig {

  @Bean
  public RuntimeAdapter runtimeAdapter(RuntimeProperties props, RestClient runtimeRestClient,
      ObjectMapper objectMapper) {
    return RuntimeAdapterSelector.choose(props, runtimeRestClient, objectMapper);
  }
}
