package com.schemaplatform.workflowchat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    RuntimeProperties.class,
    UploadProperties.class,
    RateLimitProperties.class,
    com.schemaplatform.workflowchat.auth.AuthProperties.class
})
public class PropertiesConfig {
}
