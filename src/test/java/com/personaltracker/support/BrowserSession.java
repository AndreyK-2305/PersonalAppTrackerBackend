package com.personaltracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Cliente HTTP que se comporta como el navegador de la SPA: conserva las cookies entre peticiones
 * (sesión y XSRF-TOKEN) y envía el token CSRF en la cabecera X-XSRF-TOKEN de las peticiones que
 * modifican datos. Las cookies se gestionan a mano para no depender del atributo Secure sobre HTTP.
 */
public final class BrowserSession {

    public static final String SESSION_COOKIE = "SESSION";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";

    private final RestTestClient client;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public BrowserSession(RestTestClient client) {
        this.client = client;
    }

    public EntityExchangeResult<String> get(String uri) {
        return send(client.get().uri(uri), false);
    }

    public EntityExchangeResult<String> post(String uri, Object body) {
        return send(client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body), true);
    }

    public EntityExchangeResult<String> postWithoutCsrf(String uri, Object body) {
        return send(client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body), false);
    }

    public EntityExchangeResult<String> patch(String uri, Object body) {
        return send(client.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body), true);
    }

    public EntityExchangeResult<String> put(String uri, Object body) {
        return send(client.put().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body), true);
    }

    public EntityExchangeResult<String> delete(String uri) {
        return send(client.delete().uri(uri), true);
    }

    /** Obtiene el token CSRF (como al cargar la SPA) e inicia sesión; falla si no responde 200. */
    public void login(String email, String password) {
        get("/api/v1/auth/registration");
        EntityExchangeResult<String> result =
                post("/api/v1/auth/login", Map.of("email", email, "password", password));
        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public static Object json(EntityExchangeResult<String> result, String path) {
        return JsonPath.read(result.getResponseBody(), path);
    }

    private EntityExchangeResult<String> send(RestTestClient.RequestHeadersSpec<?> request, boolean withCsrf) {
        request.cookies(jar -> cookies.forEach(jar::add));
        if (withCsrf && cookies.containsKey(CSRF_COOKIE)) {
            request.header("X-XSRF-TOKEN", cookies.get(CSRF_COOKIE));
        }
        EntityExchangeResult<String> result = request.exchange().returnResult(String.class);
        result.getResponseCookies().values().stream().flatMap(List::stream).forEach(this::remember);
        return result;
    }

    private void remember(ResponseCookie cookie) {
        if (cookie.getMaxAge().isZero() || cookie.getValue().isEmpty()) {
            cookies.remove(cookie.getName());
        } else {
            cookies.put(cookie.getName(), cookie.getValue());
        }
    }
}
