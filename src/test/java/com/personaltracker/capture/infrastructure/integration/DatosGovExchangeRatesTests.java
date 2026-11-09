package com.personaltracker.capture.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DatosGovExchangeRatesTests {

    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 17);

    private MockRestServiceServer server;
    private DatosGovExchangeRates rates;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://datos.example");
        server = MockRestServiceServer.bindTo(builder).build();
        rates = new DatosGovExchangeRates(builder.build());
    }

    private static String row(String value, String from, String to) {
        return "[{\"valor\":\"" + value + "\",\"unidad\":\"COP\",\"vigenciadesde\":\"" + from
                + "T00:00:00.000\",\"vigenciahasta\":\"" + to + "T00:00:00.000\"}]";
    }

    @Test
    void asksForTheLatestRateInForceOnThatDateAndOnlySendsTheDate() {
        server.expect(requestTo(Matchers.startsWith("https://datos.example" + DatosGovExchangeRates.PATH)))
                .andExpect(method(HttpMethod.GET))
                // "vigenciadesde <= '2026-10-17T00:00:00.000'", codificado por completo.
                .andExpect(queryParam("$where", "vigenciadesde%20%3C%3D%20%272026-10-17T00%3A00%3A00.000%27"))
                .andExpect(queryParam("$order", "vigenciadesde%20DESC"))
                .andExpect(queryParam("$limit", "1"))
                .andRespond(withSuccess(row("3174.67", "2026-10-17", "2026-10-19"), MediaType.APPLICATION_JSON));

        assertThat(rates.copPerUsd(SATURDAY)).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("3174.67"));
        server.verify();
    }

    @Test
    void ratesThatCoverTheDateAreCached() {
        server.expect(ExpectedCount.once(), requestTo(Matchers.startsWith("https://datos.example")))
                .andRespond(withSuccess(row("3174.67", "2026-10-17", "2026-10-19"), MediaType.APPLICATION_JSON));

        rates.copPerUsd(SATURDAY);
        assertThat(rates.copPerUsd(SATURDAY)).isPresent();
        server.verify();
    }

    @Test
    void anOlderRateIsUsedButNotCached() {
        // La TRM de hoy aún no está publicada: se usa la anterior y se vuelve a preguntar después.
        server.expect(ExpectedCount.twice(), requestTo(Matchers.startsWith("https://datos.example")))
                .andRespond(withSuccess(row("3237.83", "2026-11-07", "2026-11-09"), MediaType.APPLICATION_JSON));

        LocalDate tuesday = LocalDate.of(2026, 11, 10);
        assertThat(rates.copPerUsd(tuesday)).isPresent();
        assertThat(rates.copPerUsd(tuesday)).isPresent();
        server.verify();
    }

    @Test
    void failuresAndEmptyAnswersGiveNoRate() {
        server.expect(requestTo(Matchers.startsWith("https://datos.example"))).andRespond(withServerError());
        server.expect(requestTo(Matchers.startsWith("https://datos.example")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(rates.copPerUsd(SATURDAY)).isEmpty();
        assertThat(rates.copPerUsd(SATURDAY)).isEmpty();
        server.verify();
    }
}
