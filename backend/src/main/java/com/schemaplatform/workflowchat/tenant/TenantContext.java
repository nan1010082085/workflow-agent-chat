package com.schemaplatform.workflowchat.tenant;

import com.schemaplatform.workflowchat.auth.AuthUser;

/**
 * 当前请求的租户与用户上下文。由 TenantContextFilter 注入。
 */
public final class TenantContext {

  private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(String tenantId, String userId) {
    HOLDER.set(new Holder(tenantId, userId, null, null));
  }

  public static void set(String tenantId, String userId, AuthUser authUser, String accessToken) {
    HOLDER.set(new Holder(tenantId, userId, authUser, accessToken));
  }

  public static Holder get() {
    Holder h = HOLDER.get();
    if (h == null) {
      throw new IllegalStateException("无租户上下文，请求未通过 TenantContextFilter");
    }
    return h;
  }

  public static String tenantId() {
    return get().tenantId();
  }

  public static String userId() {
    return get().userId();
  }

  public static AuthUser authUser() {
    Holder h = HOLDER.get();
    return h == null ? null : h.authUser();
  }

  public static String accessToken() {
    Holder h = HOLDER.get();
    return h == null ? null : h.accessToken();
  }

  public static void clear() {
    HOLDER.remove();
  }

  public record Holder(String tenantId, String userId, AuthUser authUser, String accessToken) {}
}
