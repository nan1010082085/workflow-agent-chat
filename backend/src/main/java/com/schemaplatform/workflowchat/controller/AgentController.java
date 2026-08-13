package com.schemaplatform.workflowchat.controller;

import com.schemaplatform.workflowchat.runtime.AgentDto;
import com.schemaplatform.workflowchat.service.AgentCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Catalog 接口。GET /api/chat/agents
 * 只返回当前租户可见、已发布的 Workflow Agent。见 ISS-01。
 */
@RestController
@RequestMapping("/api/chat/agents")
public class AgentController {

  private final AgentCatalogService agentCatalogService;

  public AgentController(AgentCatalogService agentCatalogService) {
    this.agentCatalogService = agentCatalogService;
  }

  @GetMapping
  public List<AgentDto> listAgents() {
    return agentCatalogService.listAgents();
  }
}
