package co.edu.corhuila.property.notification.domain.model;

import java.util.Map;

/** The text the guest reads for each reason a Reserva was cancelled (DOC4, data-model.md). */
public final class MotivoCierre {

    static final String GENERICO = "no se pudo completar la reserva";

    private static final Map<String, String> TEXTOS = Map.of(
            "CANCELADA_POR_HUESPED", "la cancelaste tú",
            "SAGA_COMPENSADA", GENERICO,
            "FONDOS_INSUFICIENTES", "la tarjeta no tiene fondos suficientes",
            "TARJETA_RECHAZADA", "la tarjeta fue rechazada",
            "TARJETA_VENCIDA", "la tarjeta está vencida",
            "PASARELA_NO_DISPONIBLE", "el servicio de pagos no estaba disponible");

    private MotivoCierre() { }

    /** A reason Booking may add later reads as the generic text: the guest is still told. */
    public static String texto(String motivo) {
        return motivo == null ? GENERICO : TEXTOS.getOrDefault(motivo, GENERICO);
    }
}
