package com.personaltracker.capture.infrastructure.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.personaltracker.capture.application.port.out.ExchangeRates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * TRM oficial del conjunto de datos abiertos de la Superintendencia Financiera en datos.gov.co
 * (ADR-0017). Es pública, no pide clave y la consulta solo lleva la fecha.
 *
 * <p>Pide la tasa más reciente con vigencia desde esa fecha o antes, así que un fin de semana o un
 * festivo usa la del último día hábil. Las tasas cuya vigencia ya cubre la fecha no cambian, así que
 * se guardan en memoria. Si la fuente falla, devuelve vacío y la notificación queda "Por revisar".
 */
class DatosGovExchangeRates implements ExchangeRates {

    static final String PATH = "/resource/32sa-8pi3.json";

    private static final Logger log = LoggerFactory.getLogger(DatosGovExchangeRates.class);

    private final RestClient client;
    private final Map<LocalDate, BigDecimal> cache = new ConcurrentHashMap<>();

    DatosGovExchangeRates(RestClient client) {
        this.client = client;
    }

    @Override
    public Optional<BigDecimal> copPerUsd(LocalDate date) {
        BigDecimal cached = cache.get(date);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            TrmRow[] rows = client.get()
                    .uri(PATH + "?$where={where}&$order={order}&$limit=1",
                            "vigenciadesde <= '" + date + "T00:00:00.000'", "vigenciadesde DESC")
                    .retrieve()
                    .body(TrmRow[].class);
            if (rows == null || rows.length == 0 || rows[0].valor() == null) {
                return Optional.empty();
            }
            TrmRow row = rows[0];
            BigDecimal rate = new BigDecimal(row.valor());
            if (row.coversDate(date)) {
                cache.put(date, rate);
            }
            return Optional.of(rate);
        } catch (RestClientException | NumberFormatException failure) {
            log.warn("No fue posible consultar la TRM del {}: {}", date, failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Fila del conjunto de datos; los valores llegan como texto. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TrmRow(String valor, String vigenciadesde, String vigenciahasta) {

        boolean coversDate(LocalDate date) {
            return vigenciahasta != null && !LocalDateTime.parse(vigenciahasta).toLocalDate().isBefore(date);
        }
    }
}
