package co.edu.corhuila.property.notification.application.port.out;

import co.edu.corhuila.property.notification.domain.model.Canal;
import co.edu.corhuila.property.notification.domain.model.EventoReserva;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** processed_event and delivery_log (06-data/models.md). */
public interface EntregasStore {

    boolean procesado(UUID eventId);

    /** In one transaction: a SENT row of delivery_log per channel sent, and the eventId. */
    void registrar(EventoReserva evento, List<Canal> enviados, Instant ahora);
}
