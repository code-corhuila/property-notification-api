package co.edu.corhuila.property.notification.adapter.in.broker;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The link to RabbitMQ as the broker user notification, which may only read its queue
 * (domain-events.md, "Broker topology"). One message at a time (prefetch 1), acknowledged only
 * after it was applied. If the broker is down at start it tries again; once connected, the
 * client's automatic recovery brings the connection and the consumer back.
 */
public class RabbitReservasListener implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RabbitReservasListener.class);

    private final ConnectionFactory factory = new ConnectionFactory();
    private final String queue;
    private final ReservasConsumer consumer;
    private final Duration reconnect;
    private volatile Connection connection;

    public RabbitReservasListener(String host, int port, String user, String password, String queue,
                                  ReservasConsumer consumer, Duration reconnect) {
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setClientProperties(new java.util.HashMap<>(java.util.Map.of("connection_name", "notification-consumer")));
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(reconnect.toMillis());
        this.queue = queue;
        this.consumer = consumer;
        this.reconnect = reconnect;
    }

    /** Blocks until connected and consuming. */
    public void start() throws InterruptedException {
        while (true) {
            try {
                connection = factory.newConnection();
                Channel channel = connection.createChannel();
                channel.basicQos(1);
                DeliverCallback deliver = (tag, message) -> {
                    long delivery = message.getEnvelope().getDeliveryTag();
                    if (consumer.handle(message.getBody(), message.getProperties().getCorrelationId())) {
                        channel.basicAck(delivery, false);
                    } else {
                        channel.basicNack(delivery, false, false);     // to notification.reservas.dlq
                    }
                };
                channel.basicConsume(queue, false, deliver, tag -> { });
                log.info("consuming queue={}", queue);
                return;
            } catch (Exception e) {
                log.warn("broker unavailable ({}); reconnecting in {}", e.getClass().getSimpleName(), reconnect);
                close();
                Thread.sleep(reconnect.toMillis());
            }
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception ignored) {
            // closing anyway
        }
    }
}
