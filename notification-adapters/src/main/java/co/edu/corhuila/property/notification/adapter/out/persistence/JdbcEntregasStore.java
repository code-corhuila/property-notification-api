package co.edu.corhuila.property.notification.adapter.out.persistence;

import co.edu.corhuila.property.notification.application.port.out.EntregasStore;
import co.edu.corhuila.property.notification.domain.model.Canal;
import co.edu.corhuila.property.notification.domain.model.EventoReserva;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * notification.processed_event and notification.delivery_log (property-notification-db), as
 * notification_app: written in one transaction (services/08-notification/events.md). ON CONFLICT
 * absorbs a concurrent duplicate; uq_delivery_log_event_channel keeps one row per event and channel.
 */
public class JdbcEntregasStore implements EntregasStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcEntregasStore(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public boolean procesado(UUID eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM notification.processed_event WHERE event_id = ?)", Boolean.class, eventId));
    }

    @Override
    public void registrar(EventoReserva evento, List<Canal> enviados, Instant ahora) {
        Timestamp at = Timestamp.from(ahora);
        tx.executeWithoutResult(status -> {
            for (Canal canal : enviados) {
                jdbc.update("INSERT INTO notification.delivery_log (user_id, source_event_type, source_event_id, channel, "
                        + "delivery_status, sent_at) VALUES (?, ?, ?, ?, 'SENT', ?) "
                        + "ON CONFLICT (source_event_id, channel) DO NOTHING",
                        evento.userId(), evento.tipo().nombre(), evento.eventId(), canal.name(), at);
            }
            jdbc.update("INSERT INTO notification.processed_event (event_id, event_type, processed_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT (event_id) DO NOTHING", evento.eventId(), evento.tipo().nombre(), at);
        });
    }
}
