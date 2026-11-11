package co.edu.corhuila.property.notification.application.port.out;

import co.edu.corhuila.property.notification.domain.model.Preferencias;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Where the preferences of each user live: the table notification_preference. */
public interface PreferenciasRepository {

    /** The saved preferences, or empty when the user never changed one. */
    Optional<Preferencias> find(UUID userId);

    /**
     * Applies the change to the current preferences (the defaults when there is no
     * row) and stores the result, atomically: two changes at the same time do not
     * lose each other's channel. Returns what was stored.
     */
    Preferencias update(UUID userId, UnaryOperator<Preferencias> change);
}
