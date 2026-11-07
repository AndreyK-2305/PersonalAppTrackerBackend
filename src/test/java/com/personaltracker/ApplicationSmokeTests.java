package com.personaltracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.shared.infrastructure.web.CorrelationIdFilter;
import com.personaltracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Arranca la aplicación completa contra PostgreSQL real y verifica la base técnica: migraciones,
 * health check, seguridad por defecto, CORS y documentación deshabilitada fuera del perfil local.
 */
@IntegrationTest
class ApplicationSmokeTests {

    @Autowired
    RestTestClient client;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void appliesFlywayMigrationsAndCreatesModuleSchemas() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        Integer moduleSchemas = jdbc.queryForObject(
                "select count(*) from information_schema.schemata where schema_name in ('identity', 'finance')",
                Integer.class);

        assertThat(applied).isPositive();
        assertThat(moduleSchemas).isEqualTo(2);
    }

    @Test
    void exposesPublicHealthAndReadinessEndpoints() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");

        client.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void rejectsUnauthenticatedApiRequestsWithProblemDetail() {
        client.get().uri("/api/v1/accounts").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists(CorrelationIdFilter.HEADER)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("unauthorized")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void rejectsCrossOriginRequestsFromUnlistedOrigins() {
        client.options().uri("/api/v1/accounts")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void keepsApiDocumentationDisabledOutsideLocalProfile() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isNotFound();
    }
}
