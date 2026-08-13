package com.schemaplatform.workflowchat.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemaplatform.workflowchat.config.RuntimeProperties;
import org.springframework.web.client.RestClient;

/**
 * Runtime adapter 选择器。根据配置决定使用真实 REST adapter 还是 mock adapter。
 * 配置项：runtime.mock-enabled / runtime.workflow-key（见 ISS-07）。
 *
 * <p>实际 bean 注册在 RuntimeAdapterConfig 中完成，此类供显式选择。
 */
public final class RuntimeAdapterSelector {

  private RuntimeAdapterSelector() {}

  public static RuntimeAdapter choose(RuntimeProperties props, RestClient restClient,
      ObjectMapper objectMapper) {
    if (props.useMock()) {
      return new RuntimeMockAdapter();
    }
    return new RuntimeRestAdapter(restClient, props, objectMapper);
  }
}
