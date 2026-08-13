package com.schemaplatform.workflowchat.tenant;

/**
 * 当前请求的租户与用户上下文。由 TenantContextFilter 注入。
 *
 * <p>凭证链路待与平台对齐（见 docs/RUNTIME_ISSUES.md ISS-03）：
 * 当前为开发态，从 X-Tenant-Id / X-User-Id 头注入；生产态替换为 JWT 解析。
 */
public final class TenantContext {

  private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(String tenantId, String userId) {
    HOLDER.set(new Holder(tenantId, userId));
  }

  public static Holder get() {
    Holder h = HOLDER.get();
    if (h == null) {
      throw new IllegalStateException("无租户上下文，请求未通过 TenantContextFilter 或缺少租户头");
    }
    return h;
  }

  public static String tenantId() {
    return get().tenantId();
  }

  public static String userId() {
    return get().userId();
  }

  public static void clear() {
    HOLDER.remove();
  }

  public record Holder(String tenantId, String userId) {}
}
