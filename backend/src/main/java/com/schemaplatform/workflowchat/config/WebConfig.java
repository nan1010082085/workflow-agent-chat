package com.schemaplatform.workflowchat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 配置。开发态允许前端 dev server 跨域；生产态通过配置收紧。
 * 配置项：chat.cors.allowed-origins（逗号分隔），默认 *。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final String allowedOrigins;

  public WebConfig(@Value("${chat.cors.allowed-origins:*}") String allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns(allowedOrigins.split(","))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
