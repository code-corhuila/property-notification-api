package co.edu.corhuila.property.notification.application.port.out;

import co.edu.corhuila.property.notification.domain.model.Plantilla;
import java.util.Optional;

/** The seed of message_template (property-notification-db). */
public interface PlantillasRepository {
    Optional<Plantilla> find(String codigo);
}
