package com.schemaplatform.workflowchat.service;

import com.schemaplatform.workflowchat.runtime.AgentDto;
import com.schemaplatform.workflowchat.runtime.RuntimeAdapter;
import com.schemaplatform.workflowchat.runtime.RuntimeUnavailableException;
import com.schemaplatform.workflowchat.tenant.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Agent Catalog 服务。调用 RuntimeAdapter 获取已发布 Agent。
 * Runtime 不可用时抛出基础设施错误，不能伪装成「暂无助手」。
 */
@Service
public class AgentCatalogService {

  private final RuntimeAdapter runtimeAdapter;

  public AgentCatalogService(RuntimeAdapter runtimeAdapter) {
    this.runtimeAdapter = runtimeAdapter;
  }

  public List<AgentDto> listAgents() {
    String tenantId = TenantContext.tenantId();
    return runtimeAdapter.listAgents(tenantId);
  }

  public AgentDto getAgent(String agentId) {
    return listAgents().stream()
        .filter(a -> agentId.equals(a.id()) || agentId.equals(a.slug()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Agent 不存在或未发布: " + agentId));
  }
}
