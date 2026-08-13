package com.schemaplatform.workflowchat.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * 校验平台签发的 access JWT（与 Schema Platform 共用密钥）。
 */
@Service
public class JwtService {

  private final AuthProperties props;

  public JwtService(AuthProperties props) {
    this.props = props;
  }

  /**
   * 解析并校验 Bearer access token。
   *
   * @throws UnauthorizedException 密钥未配置、签名错误、过期或类型不对
   */
  public AuthUser parseAccessToken(String token) {
    String secret = props.getJwtSecret();
    if (secret == null || secret.isBlank()) {
      throw new UnauthorizedException("服务未配置 JWT 密钥");
    }
    if (token == null || token.isBlank()) {
      throw new UnauthorizedException("缺少访问令牌");
    }
    try {
      SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
      Claims claims = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token.trim())
          .getPayload();
      String tokenType = stringClaim(claims, "tokenType");
      if (!tokenType.isBlank() && !"access".equals(tokenType)) {
        throw new UnauthorizedException("令牌类型无效");
      }
      String id = firstNonBlank(stringClaim(claims, "id"), stringClaim(claims, "sub"));
      String username = stringClaim(claims, "username");
      String tenantId = firstNonBlank(stringClaim(claims, "tenantId"), "000000");
      if (id.isBlank()) {
        throw new UnauthorizedException("令牌缺少用户标识");
      }
      @SuppressWarnings("unchecked")
      List<String> roles = claims.get("roles") instanceof List<?> list
          ? list.stream().map(String::valueOf).toList()
          : List.of();
      String deptId = stringClaim(claims, "deptId");
      if (deptId.isBlank()) deptId = null;
      return new AuthUser(id, username.isBlank() ? id : username, tenantId, roles, deptId);
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      throw new UnauthorizedException("登录已失效，请重新登录");
    }
  }

  private static String stringClaim(Claims claims, String name) {
    Object v = claims.get(name);
    return v == null ? "" : String.valueOf(v);
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b == null ? "" : b;
  }
}
