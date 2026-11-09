package com.personaltracker.capture;

import static com.personaltracker.support.BrowserSession.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.support.BrowserSession;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Entrada de la captura (ADR-0017): tokens de dispositivo gestionados con la sesión y endpoint de
 * notificaciones autenticado solo con el token.
 */
@IntegrationTest
@SuppressWarnings("unchecked")
class CaptureEntryTests {

    private static final String TOKENS = "/api/v1/me/device-tokens";
    private static final String NOTIFICATIONS = "/api/v1/device/capture/notifications";

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    JdbcClient jdbc;

    private User user;
    private BrowserSession browser;

    @BeforeEach
    void logIn() {
        user = new TestAccounts(users, passwordHasher).create();
        browser = new BrowserSession(client);
        browser.login(user.email().value(), TestAccounts.PASSWORD);
    }

    private EntityExchangeResult<String> createToken(BrowserSession session, String name) {
        return session.post(TOKENS, Map.of("name", name, "scopes", List.of("FINANCE_CAPTURE")));
    }

    private String newSecret() {
        EntityExchangeResult<String> created = createToken(browser, "iPhone de prueba");
        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        return (String) json(created, "$.secret");
    }

    private static Map<String, Object> notification(String body) {
        return Map.of("app", "Mensajes", "title", "85540", "body", body,
                "receivedAt", "2026-11-09T10:31:00-05:00");
    }

    private EntityExchangeResult<String> send(String authorization, Object body) {
        RestTestClient.RequestBodySpec request = client.post().uri(NOTIFICATIONS).contentType(MediaType.APPLICATION_JSON);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return request.body(body).exchange().expectBody(String.class).returnResult();
    }

    private long storedNotifications() {
        return jdbc.sql("SELECT count(*) FROM capture.notifications WHERE user_id = ?")
                .param(user.id().value()).query(Long.class).single();
    }

    @Test
    void theSecretIsShownOnlyOnCreation() {
        EntityExchangeResult<String> created = createToken(browser, "iPhone de Ana");

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(created, "$.secret")).startsWith("ptd_").hasSizeGreaterThan(40);
        assertThat((String) json(created, "$.token.name")).isEqualTo("iPhone de Ana");
        assertThat((List<Object>) json(created, "$.token.scopes")).containsExactly("FINANCE_CAPTURE");

        EntityExchangeResult<String> listed = browser.get(TOKENS);
        assertThat(listed.getResponseBody()).doesNotContain((String) json(created, "$.secret")).doesNotContain("secret");
        assertThat((List<Object>) json(listed, "$[*].name")).containsExactly("iPhone de Ana");
    }

    @Test
    void aValidTokenQueuesTheNotificationAndAnExactResendIsIgnored() {
        String secret = newSecret();
        Map<String, Object> body = notification("Bancolombia: Compraste COP9.780,00 en TIENDAS ARA con tu T.Cred"
                + " *1234, el 08/11/2026 a las 08:48.");

        assertThat(send("Bearer " + secret, body).getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(send("Bearer " + secret, body).getStatus()).isEqualTo(HttpStatus.ACCEPTED);

        assertThat(storedNotifications()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM capture.notifications WHERE user_id = ?")
                .param(user.id().value()).query(String.class).single()).isEqualTo("RECEIVED");
        assertThat((String) json(browser.get(TOKENS), "$[0].lastUsedAt")).isNotNull();
    }

    @Test
    void withoutAValidTokenTheEndpointAnswers401() {
        Map<String, Object> body = notification("Bancolombia: texto");

        assertThat(send(null, body).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send("Bearer ptd_inventado", body).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send("Basic dXN1YXJpbzpjbGF2ZQ==", body).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(storedNotifications()).isZero();
    }

    @Test
    void theBrowserSessionDoesNotOpenTheDeviceEndpoint() {
        assertThat(browser.post(NOTIFICATIONS, notification("Bancolombia: texto")).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theTokenDoesNotOpenTheRestOfTheApi() {
        String secret = newSecret();

        assertThat(client.get().uri("/api/v1/me").header("Authorization", "Bearer " + secret)
                .exchange().returnResult().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(client.post().uri("/api/v1/device/otra-herramienta").header("Authorization", "Bearer " + secret)
                .exchange().returnResult().getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aRevokedTokenStopsWorking() {
        EntityExchangeResult<String> created = createToken(browser, "iPhone viejo");
        String secret = (String) json(created, "$.secret");
        String id = (String) json(created, "$.token.id");

        assertThat(browser.delete(TOKENS + "/" + id).getStatus()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(send("Bearer " + secret, notification("Bancolombia: texto")).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((Boolean) json(browser.get(TOKENS), "$[0].active")).isFalse();
    }

    @Test
    void anotherUsersTokenIsNotFoundAndNotListed() {
        String id = (String) json(createToken(browser, "iPhone de Ana"), "$.token.id");
        User intruderUser = new TestAccounts(users, passwordHasher).create();
        BrowserSession intruder = new BrowserSession(client);
        intruder.login(intruderUser.email().value(), TestAccounts.PASSWORD);

        assertThat(intruder.delete(TOKENS + "/" + id).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.delete(TOKENS + "/" + UUID.randomUUID()).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((List<?>) json(intruder.get(TOKENS), "$")).isEmpty();
    }

    @Test
    void invalidRequestsAreRejected() {
        String secret = newSecret();

        assertThat(send("Bearer " + secret, Map.of("app", "Mensajes", "body", "texto")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(createToken(browser, " ").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post(TOKENS, Map.of("name", "iPhone", "scopes", List.of())).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(client.get().uri(TOKENS).exchange().returnResult().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
