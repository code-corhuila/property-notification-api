package co.edu.corhuila.property.notification.domain.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A terminal event of a Reserva as Notification uses it (02-domain/domain-events.md). The guest
 * is metadata.userId (D-C14). Only ReservaConfirmada carries the dates; only ReservaCancelada a
 * motivo.
 */
public record EventoReserva(UUID eventId, TipoEvento tipo, UUID reservaId, UUID userId,
                            LocalDate fechaInicio, LocalDate fechaFin, String motivo, String correlationId) {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public EventoReserva {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tipo, "eventType");
        Objects.requireNonNull(reservaId, "reservaId");
        Objects.requireNonNull(userId, "metadata.userId");
        if (tipo == TipoEvento.RESERVA_CONFIRMADA && (fechaInicio == null || fechaFin == null)) {
            throw new IllegalArgumentException("ReservaConfirmada without fechaInicio and fechaFin");
        }
    }

    /** The values of the placeholders of DOC4: nombre, reservaId, and the dates or the motivo. */
    public Map<String, String> valores(String nombre) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("nombre", nombre);
        valores.put("reservaId", reservaId.toString());
        if (fechaInicio != null && fechaFin != null) {
            valores.put("fechaInicio", FECHA.format(fechaInicio));
            valores.put("fechaFin", FECHA.format(fechaFin));
        }
        if (tipo == TipoEvento.RESERVA_CANCELADA) {
            valores.put("motivo", MotivoCierre.texto(motivo));
        }
        return valores;
    }
}
