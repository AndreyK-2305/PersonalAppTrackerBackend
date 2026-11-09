package com.personaltracker.capture.infrastructure.config;

import com.personaltracker.capture.application.port.out.ExchangeRates;
import com.personaltracker.capture.domain.parsing.NotificationParsers;
import com.personaltracker.capture.infrastructure.integration.ExchangeRateClients;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans del módulo capture que no son adaptadores con anotación propia. */
@Configuration(proxyBeanMethods = false)
class CaptureConfiguration {

    @Bean
    NotificationParsers notificationParsers() {
        return NotificationParsers.all();
    }

    /** La URL es configurable para las pruebas; los tiempos de espera evitan bloquear la cola. */
    @Bean
    ExchangeRates exchangeRates(
            @Value("${app.capture.exchange-rate-url:https://www.datos.gov.co}") String baseUrl) {
        return ExchangeRateClients.datosGov(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }
}
