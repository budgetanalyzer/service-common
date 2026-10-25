package org.budgetanalyzer.smoke.servlet;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.budgetanalyzer.service.config.BaseOpenApiConfig;

/** Consumer-style OpenAPI configuration extending the shared base configuration. */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Servlet Smoke API", version = "1.0.0"))
public class SmokeOpenApiConfig extends BaseOpenApiConfig {}
