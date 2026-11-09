package com.personaltracker.capture.infrastructure.integration;

import com.personaltracker.capture.application.port.out.ExchangeRates;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Fábrica del cliente de la TRM; mantiene el adaptador sin visibilidad pública. */
public final class ExchangeRateClients {

    private ExchangeRateClients() {}

    public static ExchangeRates datosGov(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return new DatosGovExchangeRates(
                RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }
}
