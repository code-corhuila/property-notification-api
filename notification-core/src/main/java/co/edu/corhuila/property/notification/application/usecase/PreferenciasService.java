package co.edu.corhuila.property.notification.application.usecase;

import co.edu.corhuila.property.notification.application.port.in.Caller;
import co.edu.corhuila.property.notification.application.port.in.PreferenciasUseCases;
import co.edu.corhuila.property.notification.application.port.out.PreferenciasRepository;
import co.edu.corhuila.property.notification.domain.model.Preferencias;

/**
 * The permission is decided here, not in the controller (security-rules.md A01): the
 * preferences are always those of the account in the token, so a person can never read
 * or change another user's, and a service credential has none (403).
 */
public class PreferenciasService implements PreferenciasUseCases {

    private final PreferenciasRepository preferencias;

    public PreferenciasService(PreferenciasRepository preferencias) {
        this.preferencias = preferencias;
    }

    @Override
    public Preferencias consultar(Caller caller) {
        return preferencias.find(caller.personId()).orElse(Preferencias.DEFAULTS);
    }

    @Override
    public Preferencias actualizar(Caller caller, Boolean email, Boolean sms, Boolean push) {
        return preferencias.update(caller.personId(), current -> current.con(email, sms, push));
    }
}
