package co.edu.corhuila.property.notification.adapter.out.persistence;

import co.edu.corhuila.property.notification.application.port.out.PlantillasRepository;
import co.edu.corhuila.property.notification.domain.model.Canal;
import co.edu.corhuila.property.notification.domain.model.Plantilla;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** notification.message_template: the seed of property-notification-db (DOC4). Read only. */
public class JdbcPlantillasRepository implements PlantillasRepository {

    private final JdbcTemplate jdbc;

    public JdbcPlantillasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Plantilla> find(String codigo) {
        return jdbc.query("SELECT code, channel, subject, body FROM notification.message_template WHERE code = ?",
                (rs, n) -> new Plantilla(rs.getString("code"), Canal.valueOf(rs.getString("channel")),
                        rs.getString("subject"), rs.getString("body")), codigo).stream().findFirst();
    }
}
