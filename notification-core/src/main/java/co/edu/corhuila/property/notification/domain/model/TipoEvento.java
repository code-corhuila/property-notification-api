package co.edu.corhuila.property.notification.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The three terminal events of a Reserva that Notification consumes (services/08-notification/
 * events.md), each with the prefix of its templates: RESERVATION_CONFIRMED_EMAIL and so on (DOC4).
 */
public enum TipoEvento {
    RESERVA_CONFIRMADA("ReservaConfirmada", "RESERVATION_CONFIRMED"),
    RESERVA_CANCELADA("ReservaCancelada", "RESERVATION_CANCELLED"),
    RESERVA_EXPIRADA("ReservaExpirada", "RESERVATION_EXPIRED");

    private final String nombre;
    private final String prefijo;

    TipoEvento(String nombre, String prefijo) {
        this.nombre = nombre;
        this.prefijo = prefijo;
    }

    /** The eventType of the envelope, e.g. "ReservaConfirmada". */
    public String nombre() {
        return nombre;
    }

    public String plantilla(Canal canal) {
        return prefijo + "_" + canal.name();
    }

    public static Optional<TipoEvento> of(String eventType) {
        return Arrays.stream(values()).filter(t -> t.nombre.equals(eventType)).findFirst();
    }
}
