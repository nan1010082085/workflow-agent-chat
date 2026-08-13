package com.schemaplatform.workflowchat.config;

import com.schemaplatform.workflowchat.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 简易内存限流：按 tenant+user+桶 限制每分钟请求数。
 * 在 TenantContextFilter 之后执行（依赖租户头）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimitProperties props;
  private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

  public RateLimitFilter(RateLimitProperties props) {
    this.props = props;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!props.isEnabled()) return true;
    String path = request.getRequestURI();
    return path == null || !path.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String path = request.getRequestURI();
    String method = request.getMethod();
    Bucket bucket = resolveBucket(method, path);
    if (bucket != null) {
      String tenant = safe(TenantContext.tenantId());
      String user = safe(TenantContext.userId());
      String key = tenant + "|" + user + "|" + bucket.name();
      int limit = switch (bucket) {
        case UPLOAD -> props.getUploadPerMinute();
        case MESSAGE -> props.getMessagePerMinute();
        case API -> props.getApiPerMinute();
      };
      if (!tryAcquire(key, limit)) {
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        response.getWriter().write(
            "{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后再试\"}");
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  private Bucket resolveBucket(String method, String path) {
    if ("POST".equalsIgnoreCase(method) && path.matches(".*/uploads/?$")) {
      return Bucket.UPLOAD;
    }
    if ("POST".equalsIgnoreCase(method) && (
        path.matches(".*/sessions/[^/]+/messages/?$")
            || path.matches(".*/sessions/[^/]+/completions/?$")
            || path.matches(".*/sessions/[^/]+/model-turns/?$")
            || path.matches(".*/models/completions/?$"))) {
      return Bucket.MESSAGE;
    }
    if (path.startsWith("/api/")) {
      return Bucket.API;
    }
    return null;
  }

  private boolean tryAcquire(String key, int limitPerMinute) {
    long minute = System.currentTimeMillis() / 60_000L;
    WindowCounter counter = counters.compute(key, (k, existing) -> {
      if (existing == null || existing.minute.get() != minute) {
        return new WindowCounter(minute);
      }
      return existing;
    });
    return counter.count.incrementAndGet() <= limitPerMinute;
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "anonymous" : value;
  }

  private enum Bucket { UPLOAD, MESSAGE, API }

  private static final class WindowCounter {
    final AtomicLong minute;
    final AtomicInteger count = new AtomicInteger(0);

    WindowCounter(long minute) {
      this.minute = new AtomicLong(minute);
    }
  }
}
