package co.edu.corhuila.property.notification.application.port.in;

import co.edu.corhuila.property.notification.domain.model.Preferencias;

/** What notification offers over HTTP: the preferences of the caller, own only (E-16, E-17). */
public interface PreferenciasUseCases {

    /** E-16: the saved preferences, or the defaults when the caller never saved any. */
    Preferencias consultar(Caller caller);

    /** E-17: changes only the channels sent (null = not sent) and returns all of them. */
    Preferencias actualizar(Caller caller, Boolean email, Boolean sms, Boolean push);
}
