package co.edu.corhuila.property.notification.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.property.notification.application.port.in.NotificarResultado.Resultado;
import co.edu.corhuila.property.notification.application.port.out.EntregasStore;
import co.edu.corhuila.property.notification.application.port.out.PreferenciasRepository;
import co.edu.corhuila.property.notification.domain.model.Canal;
import co.edu.corhuila.property.notification.domain.model.Contacto;
import co.edu.corhuila.property.notification.domain.model.EventoReserva;
import co.edu.corhuila.property.notification.domain.model.Plantilla;
import co.edu.corhuila.property.notification.domain.model.Plantilla.Mensaje;
import co.edu.corhuila.property.notification.domain.model.Preferencias;
import co.edu.corhuila.property.notification.domain.model.TipoEvento;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** The 3 scenarios of HU-NOTIF-001 and the rules of DEC-005 and DEC-006, with fakes. */
class NotificacionServiceTest {

    static final UUID GUEST = UUID.fromString("c8f2a1b0-4444-4c3e-9a2d-000000000004");
    static final UUID RESERVA = UUID.fromString("b7e6c1d0-2222-4c3e-9a2d-000000000002");

    final Set<UUID> processed = new HashSet<>();
    final List<String> recorded = new ArrayList<>();
    final Map<UUID, Preferencias> prefs = new HashMap<>();
    final Map<UUID, Contacto> accounts = new HashMap<>(Map.of(GUEST, new Contacto("ana@example.com", "Ana")));
    final List<String> mails = new ArrayList<>();
    RuntimeException smtpFailure;

    final EntregasStore store = new EntregasStore() {
        public boolean procesado(UUID eventId) {
            return processed.contains(eventId);
        }

        public void registrar(EventoReserva e, List<Canal> enviados, Instant ahora) {
            processed.add(e.eventId());
            recorded.add(e.tipo().nombre() + enviados);
        }
    };

    final PreferenciasRepository preferencias = new PreferenciasRepository() {
        public Optional<Preferencias> find(UUID userId) {
            return Optional.ofNullable(prefs.get(userId));
        }

        public Preferencias update(UUID userId, UnaryOperator<Preferencias> change) {
            throw new UnsupportedOperationException();
        }
    };

    final NotificacionService service = new NotificacionService(store, preferencias,
            id -> Optional.ofNullable(accounts.get(id)),
            code -> Optional.of(seed(code)),
            (to, m) -> {
                if (smtpFailure != null) {
                    throw smtpFailure;
                }
                mails.add(to + " | " + m.asunto() + " | " + m.cuerpo());
            },
            Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC));

    static Plantilla seed(String code) {
        return switch (code) {
            case "RESERVATION_CONFIRMED_EMAIL" -> new Plantilla(code, Canal.EMAIL, "Tu reserva está confirmada",
                    "Hola {{nombre}}: tu reserva {{reservaId}} está confirmada, del {{fechaInicio}} al {{fechaFin}}.");
            case "RESERVATION_CANCELLED_EMAIL" -> new Plantilla(code, Canal.EMAIL, "Tu reserva fue cancelada",
                    "Hola {{nombre}}: tu reserva {{reservaId}} fue cancelada: {{motivo}}.");
            default -> new Plantilla(code, Canal.EMAIL, "Tu reserva expiró", "Hola {{nombre}}: expiró {{reservaId}}.");
        };
    }

    static EventoReserva evento(TipoEvento tipo, String motivo) {
        boolean dates = tipo == TipoEvento.RESERVA_CONFIRMADA;
        return new EventoReserva(UUID.randomUUID(), tipo, RESERVA, GUEST, dates ? LocalDate.of(2027, 9, 10) : null,
                dates ? LocalDate.of(2027, 9, 15) : null, motivo, "saga-1");
    }

    @Test
    void scenario1AConfirmedReservaSendsTheEmailAndRecordsIt() {
        assertEquals(Resultado.ENVIADO, service.notificar(evento(TipoEvento.RESERVA_CONFIRMADA, null)));
        assertEquals(List.of("ana@example.com | Tu reserva está confirmada | Hola Ana: tu reserva " + RESERVA
                + " está confirmada, del 10/09/2027 al 15/09/2027."), mails);
        assertEquals(List.of("ReservaConfirmada[EMAIL]"), recorded);
    }

    @Test
    void scenario2ACancelledReservaExplainsTheReasonAndAnExpiredOneIsSentToo() {
        service.notificar(evento(TipoEvento.RESERVA_CANCELADA, "FONDOS_INSUFICIENTES"));
        service.notificar(evento(TipoEvento.RESERVA_EXPIRADA, null));
        assertTrue(mails.get(0).endsWith("fue cancelada: la tarjeta no tiene fondos suficientes."));
        assertTrue(mails.get(1).startsWith("ana@example.com | Tu reserva expiró"));
    }

    @Test
    void scenario3TheSameEventTwiceSendsOneMessage() {
        EventoReserva e = evento(TipoEvento.RESERVA_CONFIRMADA, null);
        service.notificar(e);
        assertEquals(Resultado.DUPLICADO, service.notificar(e));
        assertEquals(1, mails.size());
        assertEquals(1, recorded.size());
    }

    @Test
    void withEmailOffNothingIsSentAndTheEventIsStillRecorded() {
        prefs.put(GUEST, new Preferencias(false, true, true));     // SMS and push send nothing (DEC-006)
        assertEquals(Resultado.SIN_CANAL, service.notificar(evento(TipoEvento.RESERVA_EXPIRADA, null)));
        assertEquals(List.of(), mails);
        assertEquals(List.of("ReservaExpirada[]"), recorded);
    }

    @Test
    void anAccountIdentityDoesNotKnowHasNoAddress() {
        accounts.clear();
        assertEquals(Resultado.SIN_CUENTA, service.notificar(evento(TipoEvento.RESERVA_EXPIRADA, null)));
        assertEquals(List.of("ReservaExpirada[]"), recorded);
    }

    @Test
    void whenTheMailCannotBeSentNothingIsRecordedAndTheFailureGoesUp() {
        smtpFailure = new IllegalStateException("smtp down");
        assertThrows(IllegalStateException.class, () -> service.notificar(evento(TipoEvento.RESERVA_EXPIRADA, null)));
        assertEquals(List.of(), recorded);
    }

    @Test
    void anUnknownReasonReadsAsTheGenericTextAndAMissingValueBreaksTheTemplate() {
        service.notificar(evento(TipoEvento.RESERVA_CANCELADA, "MOTIVO_NUEVO"));
        assertTrue(mails.get(0).endsWith("fue cancelada: no se pudo completar la reserva."));
        Plantilla broken = new Plantilla("X", Canal.EMAIL, null, "{{nombre}} {{otro}}");
        assertThrows(IllegalStateException.class, () -> broken.render(Map.of("nombre", "Ana")));
        assertEquals(new Mensaje(null, "Ana $1"), new Plantilla("Y", Canal.SMS, null, "{{nombre}}")
                .render(Map.of("nombre", "Ana $1")));
    }

    @Test
    void aConfirmationWithoutDatesIsNotAnEvent() {
        assertThrows(IllegalArgumentException.class, () -> new EventoReserva(UUID.randomUUID(),
                TipoEvento.RESERVA_CONFIRMADA, RESERVA, GUEST, null, null, null, null));
        assertEquals("Contacto[***]", new Contacto("ana@example.com", "Ana").toString());
    }
}
