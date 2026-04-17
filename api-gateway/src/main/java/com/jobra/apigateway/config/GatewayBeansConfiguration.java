package com.jobra.apigateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GatewaySecurityPathProperties.class, GatewayRateLimitProperties.class})
public class GatewayBeansConfiguration {
}
