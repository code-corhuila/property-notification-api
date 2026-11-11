package co.edu.corhuila.property.notification.application.port.out;

import co.edu.corhuila.property.notification.domain.model.Contacto;
import java.util.Optional;
import java.util.UUID;

/** Identity's E-26 with svc-notification. Empty when Identity does not know the account (404). */
public interface ContactosPort {
    Optional<Contacto> contacto(UUID userId);
}
