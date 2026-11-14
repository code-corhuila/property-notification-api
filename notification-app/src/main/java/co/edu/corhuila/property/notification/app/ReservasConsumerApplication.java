package co.edu.corhuila.property.notification.app;

import co.edu.corhuila.property.notification.adapter.in.broker.RabbitReservasListener;
import co.edu.corhuila.property.notification.adapter.in.broker.ReservasConsumer;
import co.edu.corhuila.property.notification.adapter.out.identity.IdentityContactosClient;
import co.edu.corhuila.property.notification.adapter.out.mail.SmtpCorreoSender;
import co.edu.corhuila.property.notification.adapter.out.persistence.JdbcEntregasStore;
import co.edu.corhuila.property.notification.adapter.out.persistence.JdbcPlantillasRepository;
import co.edu.corhuila.property.notification.adapter.out.persistence.JdbcPreferenciasRepository;
import co.edu.corhuila.property.notification.application.usecase.NotificacionService;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The composition root of the consumer of notification.reservas (HU-NOTIF-001): the same jar,
 * started with --reservas-consumer, as a process apart from the API, without a web server. It is
 * deliberately not a @Configuration, so the component scan of the API never loads it.
 */
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
public class ReservasConsumerApplication {

    /** Without the token of svc-notification it cannot read the address: it does not start. */
    static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required by the consumer of notification.reservas");
        }
        return value;
    }

    @Bean
    Storage storage(@Value("${notification.database.url:}") String url,
                    @Value("${notification.database.user:}") String user,
                    @Value("${notification.database.password:}") String password,
                    @Value("${notification.consumer.pool-max:2}") int poolMax,
                    @Value("${notification.database.statement-timeout-ms:5000}") int statementTimeoutMs) {
        return Storage.connect("postgres", url, user, password, poolMax, statementTimeoutMs);
    }

    @Bean
    NotificacionService notificacionService(Storage storage,
            @Value("${notification.identity.url}") String identityUrl,
            @Value("${notification.identity.service-token:}") String token,
            @Value("${notification.identity.timeout}") Duration identityTimeout,
            @Value("${notification.mail.host:}") String mailHost, @Value("${notification.mail.port}") int mailPort,
            @Value("${notification.mail.user:}") String mailUser, @Value("${notification.mail.password:}") String mailPassword,
            @Value("${notification.mail.starttls}") boolean starttls, @Value("${notification.mail.from}") String from,
            @Value("${notification.mail.timeout}") Duration mailTimeout) {
        return new NotificacionService(new JdbcEntregasStore(storage.jdbc(), storage.tx()),
                new JdbcPreferenciasRepository(storage.jdbc(), storage.tx()),
                new IdentityContactosClient(identityUrl, require(token, "SERVICE_TOKEN"), identityTimeout),
                new JdbcPlantillasRepository(storage.jdbc()),
                new SmtpCorreoSender(require(mailHost, "MAIL_HOST"), mailPort, mailUser, mailPassword, starttls, from,
                        mailTimeout),
                Clock.systemUTC());
    }

    @Bean(destroyMethod = "close")
    RabbitReservasListener listener(NotificacionService service,
            @Value("${notification.broker.host}") String host, @Value("${notification.broker.port}") int port,
            @Value("${notification.broker.user}") String user, @Value("${notification.broker.password:}") String password,
            @Value("${notification.broker.queue}") String queue, @Value("${notification.broker.reconnect}") Duration reconnect) {
        ReservasConsumer consumer = new ReservasConsumer(service, ReservasConsumer.RETRY_DELAYS, d -> Thread.sleep(d.toMillis()));
        return new RabbitReservasListener(host, port, user, require(password, "BROKER_PASSWORD"), queue, consumer, reconnect);
    }

    @Bean
    ApplicationRunner consume(RabbitReservasListener listener) {
        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(stop::countDown));
        return args -> {
            listener.start();
            stop.await();     // the client's threads deliver; this one keeps the process alive
        };
    }
}
