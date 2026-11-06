package com.personaltracker.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTests {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final AtomicReference<String> idSeenByChain = new AtomicReference<>();
    private final FilterChain chain = (request, response) -> idSeenByChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

    @Test
    void generatesIdWhenRequestHasNone() throws Exception {
        MockHttpServletResponse response = run(new MockHttpServletRequest());

        String id = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(idSeenByChain).hasValue(id);
    }

    @Test
    void reusesSafeIdSentByClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "front-1234-abcd");

        MockHttpServletResponse response = run(request);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("front-1234-abcd");
        assertThat(idSeenByChain).hasValue("front-1234-abcd");
    }

    @Test
    void replacesIdThatCouldInjectIntoLogs() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "abc\nERROR forged log line");

        MockHttpServletResponse response = run(request);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).doesNotContain("forged");
        assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER))).isNotNull();
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        run(new MockHttpServletRequest());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
