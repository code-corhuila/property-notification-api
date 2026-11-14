package co.edu.corhuila.property.notification.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.property.notification.application.port.out.PreferenciasRepository;
import co.edu.corhuila.property.notification.domain.model.Preferencias;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs against the REAL engine with the schema of property-notification-db applied, as
 * notification_app: it also proves the grants are enough. The in-memory twin runs the
 * same checks. Skipped without TEST_DATABASE_URL (Annex C); scripts/test-db.sh prepares one.
 */
class JdbcPreferenciasRepositoryIntegrationTest {

    PreferenciasRepository repository;
    UUID user;

    @BeforeEach
    void connect() {
        user = UUID.randomUUID();
        String url = System.getenv("TEST_DATABASE_URL");
        if (url == null || url.isBlank()) {
            repository = new InMemoryPreferenciasRepository();
            return;
        }
        var dataSource = new DriverManagerDataSource(url, System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"));
        repository = new JdbcPreferenciasRepository(new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @Test
    void aUserWhoNeverChangedOneHasNoRow() {
        assertTrue(repository.find(user).isEmpty());
    }

    @Test
    void theFirstChangeStartsFromTheDefaultsAndIsStored() {
        Preferencias stored = repository.update(user, p -> p.con(null, true, null));
        assertEquals(new Preferencias(true, true, true), stored);
        assertEquals(stored, repository.find(user).orElseThrow());
    }

    @Test
    void aLaterChangeKeepsTheOtherChannels() {
        repository.update(user, p -> p.con(false, null, null));
        assertEquals(new Preferencias(false, false, false), repository.update(user, p -> p.con(null, null, false)));
    }

    @Test
    void twoChangesAtTheSameTimeDoNotLoseEachOther() {
        List<CompletableFuture<Preferencias>> changes = List.of(
                CompletableFuture.supplyAsync(() -> repository.update(user, p -> p.con(false, null, null))),
                CompletableFuture.supplyAsync(() -> repository.update(user, p -> p.con(null, true, null))));
        changes.forEach(CompletableFuture::join);
        assertEquals(new Preferencias(false, true, true), repository.find(user).orElseThrow());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void theDatabaseWasReallyUsed() {
        assertTrue(repository instanceof JdbcPreferenciasRepository);
    }
}
