package co.edu.corhuila.property.notification.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.property.notification.domain.model.Canal;
import co.edu.corhuila.property.notification.domain.model.EventoReserva;
import co.edu.corhuila.property.notification.domain.model.TipoEvento;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * processed_event, delivery_log and the seed of message_template against the REAL engine with
 * property-notification-db applied, as notification_app. Skipped without TEST_DATABASE_URL (Annex C).
 */
class JdbcEntregasStoreIntegrationTest {

    JdbcTemplate jdbc;
    JdbcEntregasStore store;

    @BeforeEach
    void connect() {
        String url = System.getenv("TEST_DATABASE_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "TEST_DATABASE_URL is not set");
        var dataSource = new DriverManagerDataSource(url, System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcEntregasStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    static EventoReserva expirada() {
        return new EventoReserva(UUID.randomUUID(), TipoEvento.RESERVA_EXPIRADA, UUID.randomUUID(), UUID.randomUUID(),
                null, null, "TIMEOUT_PAGO_EXCEDIDO", "test");
    }

    @Test
    void theDeliveryAndTheEventIdAreRecordedTogetherAndOnlyOnce() {
        EventoReserva e = expirada();
        assertFalse(store.procesado(e.eventId()));
        store.registrar(e, List.of(Canal.EMAIL), Instant.now());
        store.registrar(e, List.of(Canal.EMAIL), Instant.now());     // a concurrent duplicate is absorbed
        assertTrue(store.procesado(e.eventId()));
        assertEquals("SENT ReservaExpirada", jdbc.queryForObject("SELECT delivery_status || ' ' || source_event_type "
                + "FROM notification.delivery_log WHERE source_event_id = ?", String.class, e.eventId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification.delivery_log WHERE source_event_id = ?",
                Integer.class, e.eventId()));
    }

    @Test
    void anEventWithNothingSentIsRecordedWithoutDeliveries() {
        EventoReserva e = expirada();
        store.registrar(e, List.of(), Instant.now());
        assertTrue(store.procesado(e.eventId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification.delivery_log WHERE source_event_id = ?",
                Integer.class, e.eventId()));
    }

    @Test
    void theTemplatesOfTheSeedAreRead() {
        var templates = new JdbcPlantillasRepository(jdbc);
        assertEquals("Tu reserva está confirmada", templates.find("RESERVATION_CONFIRMED_EMAIL").orElseThrow().asunto());
        assertEquals(Canal.PUSH, templates.find("RESERVATION_EXPIRED_PUSH").orElseThrow().canal());
        assertTrue(templates.find("NOPE").isEmpty());
    }
}
