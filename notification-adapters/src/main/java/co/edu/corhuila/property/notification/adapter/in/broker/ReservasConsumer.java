package co.edu.corhuila.property.notification.adapter.in.broker;

import co.edu.corhuila.property.notification.application.port.in.NotificarResultado;
import co.edu.corhuila.property.notification.application.port.in.NotificarResultado.Resultado;
import co.edu.corhuila.property.notification.domain.model.EventoReserva;
import co.edu.corhuila.property.notification.domain.model.TipoEvento;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * One message of notification.reservas (services/08-notification/events.md): read the envelope,
 * apply it, and say whether to acknowledge it. A message that cannot be read is rejected at once;
 * a failure to apply it is retried after 1 s, 2 s and 4 s, then rejected: the broker moves it to
 * notification.reservas.dlq. Every log line carries the correlationId of the event, never the body.
 */
public class ReservasConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReservasConsumer.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final List<Duration> RETRY_DELAYS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2),
            Duration.ofSeconds(4));

    /** How the consumer waits between retries; a test passes one that does not sleep. */
    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    private final NotificarResultado service;
    private final List<Duration> delays;
    private final Sleeper sleeper;

    public ReservasConsumer(NotificarResultado service, List<Duration> delays, Sleeper sleeper) {
        this.service = service;
        this.delays = List.copyOf(delays);
        this.sleeper = sleeper;
    }

    /** True to acknowledge, false to reject (to the dead-letter queue). */
    public boolean handle(byte[] body, String amqpCorrelationId) {
        MDC.put("correlationId", amqpCorrelationId == null ? "" : amqpCorrelationId);
        try {
            EventoReserva evento;
            try {
                evento = leer(body);
            } catch (RuntimeException e) {
                log.error("rejected to the dead-letter queue: not an event of a Reserva ({})", e.getClass().getSimpleName());
                return false;
            }
            if (evento.correlationId() != null) {
                MDC.put("correlationId", evento.correlationId());
            }
            return apply(evento);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private boolean apply(EventoReserva evento) {
        for (int retry = 0; ; retry++) {
            try {
                Resultado resultado = service.notificar(evento);
                log.info("event {} eventType={} eventId={}", resultado, evento.tipo().nombre(), evento.eventId());
                return true;
            } catch (RuntimeException e) {
                if (retry >= delays.size()) {
                    log.error("rejected to the dead-letter queue after {} retries eventType={} eventId={}: {}",
                            retry, evento.tipo().nombre(), evento.eventId(), e.getClass().getSimpleName());
                    return false;
                }
                log.warn("not applied eventType={} eventId={}: {}; retry {} in {}", evento.tipo().nombre(),
                        evento.eventId(), e.getClass().getSimpleName(), retry + 1, delays.get(retry));
                try {
                    sleeper.sleep(delays.get(retry));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    /** The envelope of domain-events.md, field by field. Anything missing or malformed throws. */
    static EventoReserva leer(byte[] body) {
        JsonNode e;
        try {
            e = JSON.readTree(body);
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("not JSON");
        }
        TipoEvento tipo = TipoEvento.of(e.path("eventType").asText())
                .orElseThrow(() -> new IllegalArgumentException("not an event Notification consumes"));
        JsonNode p = e.path("payload");
        JsonNode meta = e.path("metadata");
        String correlationId = meta.path("correlationId").asText(null);
        return new EventoReserva(UUID.fromString(e.path("eventId").asText()), tipo,
                UUID.fromString(p.path("reservaId").asText()), UUID.fromString(meta.path("userId").asText()),
                date(p, "fechaInicio"), date(p, "fechaFin"), p.path("motivo").asText(null),
                correlationId != null && !correlationId.isBlank() && correlationId.length() <= 128 ? correlationId : null);
    }

    private static LocalDate date(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? LocalDate.parse(payload.get(field).asText()) : null;
    }
}
