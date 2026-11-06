package com.personaltracker.shared.infrastructure.openapi;

import com.personaltracker.shared.domain.UserId;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos del documento OpenAPI. La documentación (/v3/api-docs y /swagger-ui.html) solo se
 * habilita en el perfil local; ver application-local.yml.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    static {
        // El propietario (@CurrentUser UserId) sale de la sesión: no es un parámetro de la petición.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UserId.class);
    }

    @Bean
    OpenAPI personalTrackerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Personal Tracker API")
                .version("v1")
                .description("API del backend de Personal Tracker. Los errores siguen RFC 9457 "
                        + "(application/problem+json) con un campo 'code' estable. La autenticación "
                        + "usa una cookie de sesión y el token CSRF en la cabecera X-XSRF-TOKEN."));
    }
}
