package co.edu.corhuila.property.notification.application.port.out;

import co.edu.corhuila.property.notification.domain.model.Plantilla.Mensaje;

/** The e-mail channel: SMTP to a configurable host (DEC-006). Throws when it could not send. */
public interface CorreoSender {
    void enviar(String para, Mensaje mensaje);
}
