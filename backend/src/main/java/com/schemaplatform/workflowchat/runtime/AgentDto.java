package com.schemaplatform.workflowchat.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Agent Catalog 的 Chat 侧稳定 DTO。
 * 字段映射由 AgentCatalogAdapter 完成，避免 Runtime 契约变化波及业务层。
 * 见 docs/RUNTIME_ISSUES.md ISS-01。
 */
public record AgentDto(
    String id,
    String slug,
    String name,
    String description,
    String icon,
    List<String> supportedInputs,
    boolean hitlCapable,
    String version,
    String updatedAt,
    boolean published
) {}
