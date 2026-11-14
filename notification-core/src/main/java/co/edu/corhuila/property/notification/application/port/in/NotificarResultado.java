package co.edu.corhuila.property.notification.application.port.in;

import co.edu.corhuila.property.notification.domain.model.EventoReserva;

/** HU-NOTIF-001: tell the guest how a Reserva ended. */
public interface NotificarResultado {

    enum Resultado {
        ENVIADO,        // e-mail sent and recorded
        SIN_CANAL,      // the guest turned e-mail off: nothing sent (SMS and push send nothing, DEC-006)
        SIN_CUENTA,     // Identity does not know the guest (404): no address
        DUPLICADO       // the eventId was already processed: nothing changes
    }

    Resultado notificar(EventoReserva evento);
}
