package co.edu.corhuila.property.notification.adapter.out.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** The client of E-26 against a fake Identity over real HTTP. */
class IdentityContactosClientTest {

    final List<String> requests = new ArrayList<>();
    int status = 200;
    HttpServer server;
    IdentityContactosClient client;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            requests.add(ex.getRequestURI() + " " + ex.getRequestHeaders().getFirst("Authorization") + " "
                    + ex.getRequestHeaders().getFirst("X-Correlation-Id"));
            byte[] body = "{\"id\":\"x\",\"email\":\"ana@example.com\",\"name\":\"Ana Pérez\"}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        client = new IdentityContactosClient("http://127.0.0.1:" + server.getAddress().getPort() + "/",
                "svc-token", Duration.ofSeconds(2));
        MDC.put("correlationId", "saga-1");
    }

    @AfterEach
    void stop() {
        server.stop(0);
        MDC.remove("correlationId");
    }

    @Test
    void readsTheContactWithTheServiceTokenAndTheCorrelationId() {
        UUID user = UUID.randomUUID();
        var contact = client.contacto(user).orElseThrow();
        assertEquals("ana@example.com", contact.email());
        assertEquals("Ana Pérez", contact.nombre());
        assertEquals("/api/v1/users/" + user + "/contact Bearer svc-token saga-1", requests.get(0));
    }

    @Test
    void a404IsAnAccountIdentityDoesNotKnow() {
        status = 404;
        assertTrue(client.contacto(UUID.randomUUID()).isEmpty());
    }

    @Test
    void anythingElseIsAFailureToRetry() {
        status = 503;
        assertThrows(IllegalStateException.class, () -> client.contacto(UUID.randomUUID()));
        status = 403;
        assertThrows(IllegalStateException.class, () -> client.contacto(UUID.randomUUID()));
    }
}
