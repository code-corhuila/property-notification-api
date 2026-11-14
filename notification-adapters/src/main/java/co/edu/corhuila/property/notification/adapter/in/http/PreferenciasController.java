package co.edu.corhuila.property.notification.adapter.in.http;

import co.edu.corhuila.property.notification.adapter.in.http.ApiError.FieldError;
import co.edu.corhuila.property.notification.adapter.in.http.ApiError.ValidationException;
import co.edu.corhuila.property.notification.application.port.in.Caller;
import co.edu.corhuila.property.notification.application.port.in.PreferenciasUseCases;
import co.edu.corhuila.property.notification.domain.model.Preferencias;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** E-16 and E-17 (notification-endpoint-fiches.md): the preferences of the user of the token. */
@RestController
@RequestMapping("/api/v1/preferencias")
public class PreferenciasController {

    private static final Set<String> CHANNELS = Set.of("email", "sms", "push");

    private final PreferenciasUseCases preferencias;

    public PreferenciasController(PreferenciasUseCases preferencias) {
        this.preferencias = preferencias;
    }

    /** The response of both operations: every channel, always (PreferenciasResponse). */
    public record PreferenciasResponse(boolean email, boolean sms, boolean push) {
        static PreferenciasResponse of(Preferencias p) {
            return new PreferenciasResponse(p.email(), p.sms(), p.push());
        }
    }

    @GetMapping
    public PreferenciasResponse consultar(HttpServletRequest request) {
        return PreferenciasResponse.of(preferencias.consultar(caller(request)));
    }

    /**
     * Partial: only the channels sent change. Each one sent must be true or false, and a
     * member that is not a channel is rejected instead of being ignored in silence.
     */
    @PatchMapping
    public PreferenciasResponse actualizar(HttpServletRequest request, @RequestBody JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ValidationException("the body must be a JSON object",
                    List.of(new FieldError("body", "must be an object with email, sms or push")));
        }
        List<FieldError> details = new ArrayList<>();
        body.fieldNames().forEachRemaining(name -> {
            if (!CHANNELS.contains(name)) {
                details.add(new FieldError(name, "is not a channel: email, sms or push"));
            } else if (!body.get(name).isBoolean()) {
                details.add(new FieldError(name, "must be true or false"));
            }
        });
        if (!details.isEmpty()) {
            throw new ValidationException("the request has invalid fields", details);
        }
        return PreferenciasResponse.of(preferencias.actualizar(caller(request),
                channel(body, "email"), channel(body, "sms"), channel(body, "push")));
    }

    private static Boolean channel(JsonNode body, String name) {
        return body.has(name) ? body.get(name).booleanValue() : null;
    }

    /** Who calls, from the claims AuthFilter verified. */
    private static Caller caller(HttpServletRequest request) {
        JsonNode claims = (JsonNode) request.getAttribute(AuthFilter.CLAIMS_ATTRIBUTE);
        List<String> roles = new ArrayList<>();
        claims.path("roles").forEach(r -> roles.add(r.asText()));
        return new Caller(claims.path("sub").asText(), roles, "service".equals(claims.path("scope").asText()));
    }
}
