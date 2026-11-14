package co.edu.corhuila.property.notification.adapter.out.identity;

import co.edu.corhuila.property.notification.application.port.out.ContactosPort;
import co.edu.corhuila.property.notification.domain.model.Contacto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * E-26 of Identity, GET /api/v1/users/{id}/contact, with the service token svc-notification
 * (DEC-005), inside the network platform. 200 is the contact, 404 an account Identity does not
 * know. Anything else is a failure: the consumer retries the whole event. The answer is never
 * logged nor kept.
 */
public class IdentityContactosClient implements ContactosPort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final String token;
    private final Duration timeout;

    public IdentityContactosClient(String baseUrl, String token, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.timeout = timeout;
    }

    @Override
    public Optional<Contacto> contacto(UUID userId) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/users/" + userId + "/contact"))
                .timeout(timeout).header("Authorization", "Bearer " + token).header("Accept", "application/json").GET();
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("identity did not answer", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while calling identity", e);
        }
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("identity answered " + response.statusCode() + " to E-26");
        }
        try {
            JsonNode body = JSON.readTree(response.body());
            return Optional.of(new Contacto(body.path("email").asText(), body.path("name").asText()));
        } catch (IOException e) {
            throw new UncheckedIOException("identity answered E-26 with something that is not JSON", e);
        }
    }
}
