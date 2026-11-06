package com.personaltracker.shared.infrastructure.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Orígenes autorizados para peticiones de otro origen. Por defecto ninguno: el frontend se sirve
 * desde el mismo origen que la API mediante proxy.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
    }
}
