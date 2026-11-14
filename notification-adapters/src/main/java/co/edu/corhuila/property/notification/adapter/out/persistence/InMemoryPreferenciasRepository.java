package co.edu.corhuila.property.notification.adapter.out.persistence;

import co.edu.corhuila.property.notification.application.port.out.PreferenciasRepository;
import co.edu.corhuila.property.notification.domain.model.Preferencias;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/** For tests and local development only (PERSISTENCE=memory): nothing persists (norm 5.5.2). */
public class InMemoryPreferenciasRepository implements PreferenciasRepository {

    private final Map<UUID, Preferencias> byUser = new ConcurrentHashMap<>();

    @Override
    public Optional<Preferencias> find(UUID userId) {
        return Optional.ofNullable(byUser.get(userId));
    }

    @Override
    public Preferencias update(UUID userId, UnaryOperator<Preferencias> change) {
        return byUser.compute(userId, (id, current) -> change.apply(current != null ? current : Preferencias.DEFAULTS));
    }
}
