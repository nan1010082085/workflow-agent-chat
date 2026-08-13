package com.schemaplatform.workflowchat.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 租户上下文过滤器。从请求头注入 tenantId/userId。
 *
 * <p>开发态：读取 X-Tenant-Id / X-User-Id，缺失时给开发默认值。
 * 生产态：替换为 JWT 解析（见 ISS-03）。该 filter 是租户隔离的第一道防线。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

  private static final String TENANT_HEADER = "X-Tenant-Id";
  private static final String USER_HEADER = "X-User-Id";
  private static final String DEV_TENANT = "dev-tenant";
  private static final String DEV_USER = "dev-user";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String tenantId = request.getHeader(TENANT_HEADER);
    String userId = request.getHeader(USER_HEADER);
    // 开发态兜底；生产态应拒绝无租户头请求
    if (tenantId == null || tenantId.isBlank()) {
      tenantId = DEV_TENANT;
    }
    if (userId == null || userId.isBlank()) {
      userId = DEV_USER;
    }
    TenantContext.set(tenantId, userId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path == null
        || path.startsWith("/actuator")
        || path.startsWith("/actuator-lite")
        || path.equals("/")
        || path.startsWith("/error");
  }
}
