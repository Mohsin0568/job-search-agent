package com.systa.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The OpenAPI definition's header. springdoc builds the paths and schemas from the controllers;
 * it is served at /v3/api-docs (.yaml) with Swagger UI at /swagger-ui.html, and kept in the repo
 * as openapi.yaml (see OpenApiSpecTest).
 */
@Configuration
@ConditionalOnWebApplication
public class OpenApiConfig {

    private static final String COGNITO_TOKEN = "cognito";

    @Bean
    public OpenAPI jobSearchAgentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Job Search Agent API")
                        .description("REST API behind the Job Search Agent frontend.")
                        .version("v1"))
                // Relative, so the definition is the same on every machine and works behind any host.
                .servers(List.of(new Server().url("/").description("Same origin as the frontend")))
                .components(new Components().addSecuritySchemes(COGNITO_TOKEN, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("A Cognito access token for the SPA app client.")))
                .addSecurityItem(new SecurityRequirement().addList(COGNITO_TOKEN));
    }
}
