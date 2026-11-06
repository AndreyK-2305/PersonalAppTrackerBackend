package com.personaltracker.shared.infrastructure.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos del documento OpenAPI. La documentación (/v3/api-docs y /swagger-ui.html) solo se
 * habilita en el perfil local; ver application-local.yml.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI personalTrackerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Personal Tracker API")
                .version("v1")
                .description("API del backend de Personal Tracker. Los errores siguen RFC 9457 "
                        + "(application/problem+json) con un campo 'code' estable."));
    }
}
