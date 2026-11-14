package co.edu.corhuila.property.notification.adapter.in.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.property.notification.application.port.in.NotificarResultado.Resultado;
import co.edu.corhuila.property.notification.domain.model.EventoReserva;
import co.edu.corhuila.property.notification.domain.model.TipoEvento;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Reading the envelope, the retries and what is acknowledged or rejected, without a broker. */
class ReservasConsumerTest {

    static final String EVENT = "9f1c2b3a-1111-4c3e-9a2d-000000000010";

    /** As booking-api writes it in its outbox (domain-events.md). */
    static byte[] envelope(String type, String payloadExtra) {
        return ("{\"eventId\":\"" + EVENT + "\",\"eventType\":\"" + type + "\",\"schemaVersion\":1,"
                + "\"payload\":{\"reservaId\":\"b7e6c1d0-2222-4c3e-9a2d-000000000002\","
                + "\"propiedadId\":\"a1d5e2f0-3333-4c3e-9a2d-000000000003\"" + payloadExtra + "},"
                + "\"metadata\":{\"correlationId\":\"saga-7\",\"causationId\":null,"
                + "\"userId\":\"c8f2a1b0-4444-4c3e-9a2d-000000000004\"}}").getBytes(StandardCharsets.UTF_8);
    }

    final List<String> calls = new ArrayList<>();
    final List<Duration> waits = new ArrayList<>();
    int failuresLeft;

    ReservasConsumer consumer() {
        return new ReservasConsumer(e -> {
            calls.add(e.tipo() + " " + MDC.get("correlationId"));
            if (failuresLeft-- > 0) {
                throw new IllegalStateException("smtp down");
            }
            return Resultado.ENVIADO;
        }, ReservasConsumer.RETRY_DELAYS, waits::add);
    }

    @Test
    void readsTheEnvelopeOfBooking() {
        EventoReserva e = ReservasConsumer.leer(envelope("ReservaConfirmada",
                ",\"fechaInicio\":\"2027-09-10\",\"fechaFin\":\"2027-09-15\""));
        assertEquals(TipoEvento.RESERVA_CONFIRMADA, e.tipo());
        assertEquals("c8f2a1b0-4444-4c3e-9a2d-000000000004", e.userId().toString());
        assertEquals("2027-09-15", e.fechaFin().toString());
        assertEquals("saga-7", e.correlationId());
        assertEquals("FONDOS_INSUFICIENTES",
                ReservasConsumer.leer(envelope("ReservaCancelada", ",\"motivo\":\"FONDOS_INSUFICIENTES\"")).motivo());
    }

    @Test
    void anythingElseIsNotAnEvent() {
        for (byte[] bad : List.of("not json".getBytes(), envelope("ReservaCreada", ""), envelope("ReservaConfirmada", ""),
                "{\"eventType\":\"ReservaExpirada\",\"payload\":{}}".getBytes())) {
            assertThrows(RuntimeException.class, () -> ReservasConsumer.leer(bad));
        }
    }

    @Test
    void aMalformedMessageIsRejectedAtOnceWithoutCallingTheService() {
        assertFalse(consumer().handle("{\"secret\":1}".getBytes(), "amqp-1"));
        assertEquals(List.of(), calls);
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void aFailureIsRetriedAfter1And2And4SecondsThenRejected() {
        failuresLeft = 9;
        assertFalse(consumer().handle(envelope("ReservaExpirada", ""), null));
        assertEquals(4, calls.size());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)), waits);
    }

    @Test
    void itIsAcknowledgedWhenARetryAppliesItWithTheCorrelationIdOfTheEvent() {
        failuresLeft = 1;
        assertTrue(consumer().handle(envelope("ReservaExpirada", ""), "amqp-1"));
        assertEquals(List.of("RESERVA_EXPIRADA saga-7", "RESERVA_EXPIRADA saga-7"), calls);
        assertNull(MDC.get("correlationId"));
    }
}
