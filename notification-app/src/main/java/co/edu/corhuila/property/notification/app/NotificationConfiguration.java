package co.edu.corhuila.property.notification.app;

import co.edu.corhuila.property.notification.adapter.in.http.AuthFilter;
import co.edu.corhuila.property.notification.adapter.in.http.CorrelationFilter;
import co.edu.corhuila.property.notification.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.property.notification.adapter.out.persistence.InMemoryPreferenciasRepository;
import co.edu.corhuila.property.notification.adapter.out.persistence.JdbcPreferenciasRepository;
import co.edu.corhuila.property.notification.application.port.out.PreferenciasRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Composition root: the only place that knows every concrete type. The filters
 * are registered here explicitly, in order: correlation first, so that even a
 * 401 carries its traceId.
 */
@Configuration
public class NotificationConfiguration {

    /**
     * PERSISTENCE=postgres (the default) needs DATABASE_URL, DATABASE_USER (notification_app
     * in the platform) and DATABASE_PASSWORD: without them the service does not start.
     * PERSISTENCE=memory is for tests and local development only (norm 5.5.2).
     */
    @Bean
    Storage storage(@Value("${notification.persistence:postgres}") String persistence,
                    @Value("${notification.database.url:}") String url,
                    @Value("${notification.database.user:}") String user,
                    @Value("${notification.database.password:}") String password,
                    @Value("${notification.database.pool-max:10}") int poolMax,
                    @Value("${notification.database.statement-timeout-ms:5000}") int statementTimeoutMs) {
        return Storage.connect(persistence, url, user, password, poolMax, statementTimeoutMs);
    }

    @Bean
    PreferenciasRepository preferenciasRepository(Storage storage) {
        return storage.inMemory() ? new InMemoryPreferenciasRepository()
                : new JdbcPreferenciasRepository(storage.jdbc(), storage.tx());
    }

    /**
     * JWT_PUBLIC_KEY (the PEM itself; a one-line value with literal \n escapes, as
     * a .env file holds it, is accepted) or JWT_PUBLIC_KEY_FILE. Without a key
     * the service does not start.
     */
    @Bean
    Rs256Verifier tokenVerifier(@Value("${JWT_PUBLIC_KEY:}") String pem,
                                @Value("${JWT_PUBLIC_KEY_FILE:}") String file) throws IOException {
        String key = !pem.isBlank() ? pem.replace("\\n", "\n")
                : !file.isBlank() ? Files.readString(Path.of(file)) : "";
        return new Rs256Verifier(key);
    }

    @Bean
    FilterRegistrationBean<CorrelationFilter> correlationFilter() {
        FilterRegistrationBean<CorrelationFilter> bean = new FilterRegistrationBean<>(new CorrelationFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    FilterRegistrationBean<AuthFilter> authFilter(Rs256Verifier verifier, ObjectMapper json) {
        FilterRegistrationBean<AuthFilter> bean = new FilterRegistrationBean<>(new AuthFilter(verifier, json));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
