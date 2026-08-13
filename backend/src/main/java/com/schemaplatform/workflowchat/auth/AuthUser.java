package com.schemaplatform.workflowchat.auth;

import java.util.List;

/**
 * 已校验的平台 JWT 用户声明。
 *
 * @param id       用户 ID（JWT claim {@code id}）
 * @param username 用户名
 * @param tenantId 租户
 * @param roles    角色列表
 * @param deptId   部门，可为 null
 */
public record AuthUser(
    String id,
    String username,
    String tenantId,
    List<String> roles,
    String deptId
) {
}
