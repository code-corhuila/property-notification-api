package co.edu.corhuila.property.notification.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.property.notification.adapter.in.broker.RabbitReservasListener;
import co.edu.corhuila.property.notification.adapter.in.broker.ReservasConsumer;
import co.edu.corhuila.property.notification.adapter.out.persistence.JdbcEntregasStore;
import co.edu.corhuila.property.notification.adapter.out.persistence.JdbcPlantillasRepository;
import co.edu.corhuila.property.notification.adapter.out.persistence.JdbcPreferenciasRepository;
import co.edu.corhuila.property.notification.application.usecase.NotificacionService;
import co.edu.corhuila.property.notification.domain.model.Contacto;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The 3 scenarios of HU-NOTIF-001 with a REAL RabbitMQ and the REAL schema notification (seed
 * included): only Identity and the mail server are fakes in this process. The test declares its own
 * queue with the arguments of notification.reservas (dead-letter to <queue>.dlq) and deletes both.
 * Skipped without TEST_BROKER_URL and TEST_DATABASE_URL (Annex C).
 */
class ReservasConsumerIntegrationTest {

    static final UUID GUEST = UUID.randomUUID();

    final List<String> mails = new CopyOnWriteArrayList<>();
    JdbcTemplate jdbc;
    Connection admin;
    Channel channel;
    String queue;
    RabbitReservasListener listener;

    @BeforeEach
    void start() throws Exception {
        String broker = System.getenv("TEST_BROKER_URL");
        String db = System.getenv("TEST_DATABASE_URL");
        Assumptions.assumeTrue(broker != null && !broker.isBlank() && db != null && !db.isBlank(),
                "TEST_BROKER_URL and TEST_DATABASE_URL are needed");
        var dataSource = new DriverManagerDataSource(db, System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var service = new NotificacionService(new JdbcEntregasStore(jdbc, tx), new JdbcPreferenciasRepository(jdbc, tx),
                id -> id.equals(GUEST) ? Optional.of(new Contacto("ana@example.com", "Ana")) : Optional.empty(),
                new JdbcPlantillasRepository(jdbc), (to, m) -> mails.add(to + " | " + m.asunto() + " | " + m.cuerpo()),
                Clock.systemUTC());

        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(broker);
        admin = factory.newConnection();
        channel = admin.createChannel();
        queue = "test.notification.reservas." + UUID.randomUUID().toString().substring(0, 8);
        channel.queueDeclare(queue + ".dlq", true, false, false, null);
        channel.queueDeclare(queue, true, false, false, Map.of("x-dead-letter-exchange", "",
                "x-dead-letter-routing-key", queue + ".dlq"));
        listener = new RabbitReservasListener(factory.getHost(), factory.getPort(), factory.getUsername(),
                factory.getPassword(), queue, new ReservasConsumer(service, List.of(Duration.ofMillis(10),
                Duration.ofMillis(20), Duration.ofMillis(40)), d -> Thread.sleep(d.toMillis())), Duration.ofSeconds(1));
        listener.start();
    }

    @AfterEach
    void stop() throws Exception {
        if (listener != null) {
            listener.close();
        }
        if (channel != null) {
            channel.queueDelete(queue);
            channel.queueDelete(queue + ".dlq");
            admin.close();
        }
    }

    void publish(String body) throws Exception {
        channel.basicPublish("", queue, new AMQP.BasicProperties.Builder().contentType("application/json")
                .deliveryMode(2).build(), body.getBytes(StandardCharsets.UTF_8));
    }

    static String event(String id, String type, String payloadExtra) {
        return "{\"eventId\":\"" + id + "\",\"eventType\":\"" + type + "\",\"payload\":{\"reservaId\":\""
                + "b7e6c1d0-2222-4c3e-9a2d-000000000002\"" + payloadExtra + "},\"metadata\":{\"correlationId\":"
                + "\"it-hu-notif-001\",\"userId\":\"" + GUEST + "\"}}";
    }

    /** Waits until the queue is empty and nothing is unacknowledged. */
    void drained() throws Exception {
        for (int i = 0; i < 100; i++) {
            Thread.sleep(100);
            if (channel.messageCount(queue) == 0 && channel.queueDeclarePassive(queue).getConsumerCount() == 1
                    && i > 5) {
                return;
            }
        }
    }

    int deliveries(String eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM notification.delivery_log WHERE source_event_id = ?::uuid",
                Integer.class, eventId);
    }

    @Test
    void scenario1AConfirmedReservaSendsTheEmailOfTheTemplateAndRecordsIt() throws Exception {
        String id = UUID.randomUUID().toString();
        publish(event(id, "ReservaConfirmada", ",\"fechaInicio\":\"2027-09-10\",\"fechaFin\":\"2027-09-15\""));
        drained();
        assertEquals(List.of("ana@example.com | Tu reserva está confirmada | Hola Ana: tu reserva "
                + "b7e6c1d0-2222-4c3e-9a2d-000000000002 está confirmada, del 10/09/2027 al 15/09/2027. "
                + "Puedes verla en Mis reservas. Gracias por reservar con PMS Property."), mails);
        assertEquals(1, deliveries(id));
    }

    @Test
    void scenario2ACancelledAndAnExpiredReservaExplainTheOutcome() throws Exception {
        publish(event(UUID.randomUUID().toString(), "ReservaCancelada", ",\"motivo\":\"TARJETA_RECHAZADA\""));
        publish(event(UUID.randomUUID().toString(), "ReservaExpirada", ",\"motivo\":\"TIMEOUT_PAGO_EXCEDIDO\""));
        drained();
        assertEquals(2, mails.size());
        assertTrue(mails.get(0).contains("fue cancelada: la tarjeta fue rechazada."), mails.get(0));
        assertTrue(mails.get(1).contains("expiró porque el pago no se completó en 15 minutos"), mails.get(1));
    }

    @Test
    void scenario3TheSameEventTwiceSendsOneMessagePerChannel() throws Exception {
        String id = UUID.randomUUID().toString();
        publish(event(id, "ReservaExpirada", ""));
        publish(event(id, "ReservaExpirada", ""));
        drained();
        assertEquals(1, mails.size());
        assertEquals(1, deliveries(id));
        assertEquals(0, channel.messageCount(queue + ".dlq"));
    }

    @Test
    void aMalformedMessageEndsInTheDeadLetterQueue() throws Exception {
        publish("{\"eventType\":\"ReservaExpirada\",\"payload\":{}}");
        drained();
        assertEquals(1, channel.messageCount(queue + ".dlq"));
        assertEquals(List.of(), mails);
    }
}
