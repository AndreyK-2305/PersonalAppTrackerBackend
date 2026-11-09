package com.personaltracker.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Prueba de integración: aplicación completa en un puerto aleatorio contra PostgreSQL real
 * (Testcontainers). Usar siempre esta anotación permite reutilizar el mismo contexto y el mismo
 * contenedor entre clases de prueba.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
// Las tareas programadas de la captura se desactivan: las pruebas las ejecutan a mano. La TRM apunta
// a un puerto local sin servicio: las pruebas no dependen de internet y una compra en dólares queda
// sin tasa (el cliente de la TRM se prueba aparte con un servidor simulado).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.capture.jobs.enabled=false", "app.capture.exchange-rate-url=http://127.0.0.1:9"})
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {}
