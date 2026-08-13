package com.schemaplatform.workflowchat.tenant;

import com.schemaplatform.workflowchat.auth.AuthProperties;
import com.schemaplatform.workflowchat.auth.AuthUser;
import com.schemaplatform.workflowchat.auth.JwtService;
import com.schemaplatform.workflowchat.auth.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 身份过滤器：优先解析平台 JWT；可选开发态头兜底。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

  private static final String TENANT_HEADER = "X-Tenant-Id";
  private static final String USER_HEADER = "X-User-Id";
  private static final String DEV_TENANT = "000000";
  private static final String DEV_USER = "dev-user";

  private final AuthProperties authProperties;
  private final JwtService jwtService;

  public TenantContextFilter(AuthProperties authProperties, JwtService jwtService) {
    this.authProperties = authProperties;
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (isPublicAuthPath(request) || isHealthPath(request)) {
      TenantContext.set(DEV_TENANT, "anonymous");
      try {
        filterChain.doFilter(request, response);
      } finally {
        TenantContext.clear();
      }
      return;
    }

    String bearer = extractBearer(request);
    if (bearer != null) {
      try {
        AuthUser user = jwtService.parseAccessToken(bearer);
        TenantContext.set(user.tenantId(), user.id(), user, bearer);
        filterChain.doFilter(request, response);
        return;
      } catch (UnauthorizedException e) {
        writeUnauthorized(response, e.getMessage());
        return;
      } finally {
        TenantContext.clear();
      }
    }

    if (authProperties.isRequired() && !authProperties.isAllowHeaderFallback()) {
      writeUnauthorized(response, "请先登录");
      return;
    }

    String tenantId = request.getHeader(TENANT_HEADER);
    String userId = request.getHeader(USER_HEADER);
    if (tenantId == null || tenantId.isBlank()) tenantId = DEV_TENANT;
    if (userId == null || userId.isBlank()) userId = DEV_USER;
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
        || path.equals("/")
        || path.startsWith("/error");
  }

  private static boolean isHealthPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path != null && (path.startsWith("/actuator") || path.startsWith("/actuator-lite"));
  }

  private static boolean isPublicAuthPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null) return false;
    return path.endsWith("/api/chat/auth/login")
        || path.endsWith("/api/chat/auth/refresh")
        || path.endsWith("/api/chat/auth/register")
        || path.contains("/api/chat/auth/login")
        || path.contains("/api/chat/auth/refresh")
        || path.contains("/api/chat/auth/register");
  }

  private static String extractBearer(HttpServletRequest request) {
    String auth = request.getHeader("Authorization");
    if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
    String token = auth.substring(7).trim();
    return token.isEmpty() ? null : token;
  }

  private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    String safe = message == null ? "未登录" : message.replace("\"", "'");
    response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + safe + "\"}");
  }
}
