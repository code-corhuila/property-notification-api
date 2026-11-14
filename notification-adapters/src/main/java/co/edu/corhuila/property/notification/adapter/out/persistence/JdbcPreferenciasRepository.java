package co.edu.corhuila.property.notification.adapter.out.persistence;

import co.edu.corhuila.property.notification.application.port.out.PreferenciasRepository;
import co.edu.corhuila.property.notification.domain.model.Preferencias;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The table notification.notification_preference (property-notification-db), as
 * notification_app. Parameterized SQL only. The row is created the first time a
 * user changes a preference; until then the user has the defaults.
 */
public class JdbcPreferenciasRepository implements PreferenciasRepository {

    private static final RowMapper<Preferencias> ROW = (rs, n) ->
            new Preferencias(rs.getBoolean("email_enabled"), rs.getBoolean("sms_enabled"), rs.getBoolean("push_enabled"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcPreferenciasRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<Preferencias> find(UUID userId) {
        return jdbc.query("SELECT email_enabled, sms_enabled, push_enabled FROM notification.notification_preference "
                + "WHERE user_id = ?", ROW, userId).stream().findFirst();
    }

    /**
     * Inserts the defaults if there is no row (a concurrent insert is absorbed by ON
     * CONFLICT), then locks the row, applies the change and writes it back, all in one
     * transaction: a parallel PATCH waits instead of overwriting another channel.
     */
    @Override
    public Preferencias update(UUID userId, UnaryOperator<Preferencias> change) {
        return tx.execute(status -> {
            jdbc.update("INSERT INTO notification.notification_preference (user_id) VALUES (?) "
                    + "ON CONFLICT (user_id) DO NOTHING", userId);
            Preferencias current = jdbc.queryForObject("SELECT email_enabled, sms_enabled, push_enabled "
                    + "FROM notification.notification_preference WHERE user_id = ? FOR UPDATE", ROW, userId);
            Preferencias next = change.apply(current);
            jdbc.update("UPDATE notification.notification_preference SET email_enabled = ?, sms_enabled = ?, "
                    + "push_enabled = ?, updated_at = now() WHERE user_id = ?", next.email(), next.sms(), next.push(), userId);
            return next;
        });
    }
}
