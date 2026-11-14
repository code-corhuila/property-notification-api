package co.edu.corhuila.property.notification.application.usecase;

import co.edu.corhuila.property.notification.application.port.in.NotificarResultado;
import co.edu.corhuila.property.notification.application.port.out.ContactosPort;
import co.edu.corhuila.property.notification.application.port.out.CorreoSender;
import co.edu.corhuila.property.notification.application.port.out.EntregasStore;
import co.edu.corhuila.property.notification.application.port.out.PlantillasRepository;
import co.edu.corhuila.property.notification.application.port.out.PreferenciasRepository;
import co.edu.corhuila.property.notification.domain.model.Canal;
import co.edu.corhuila.property.notification.domain.model.Contacto;
import co.edu.corhuila.property.notification.domain.model.EventoReserva;
import co.edu.corhuila.property.notification.domain.model.Plantilla;
import co.edu.corhuila.property.notification.domain.model.Preferencias;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * HU-NOTIF-001 (services/08-notification/events.md): once per eventId, read the guest's
 * preferences; with e-mail on, read the address from Identity (E-26), render the template of the
 * event and send it. Then record what was sent and the eventId together. SMS and push send and
 * record nothing in MVP 1 (DEC-006). A failure (Identity, the template, SMTP) records nothing and
 * is thrown: the consumer retries, and the event is never lost.
 */
public class NotificacionService implements NotificarResultado {

    private final EntregasStore entregas;
    private final PreferenciasRepository preferencias;
    private final ContactosPort contactos;
    private final PlantillasRepository plantillas;
    private final CorreoSender correo;
    private final Clock clock;

    public NotificacionService(EntregasStore entregas, PreferenciasRepository preferencias, ContactosPort contactos,
                               PlantillasRepository plantillas, CorreoSender correo, Clock clock) {
        this.entregas = entregas;
        this.preferencias = preferencias;
        this.contactos = contactos;
        this.plantillas = plantillas;
        this.correo = correo;
        this.clock = clock;
    }

    @Override
    public Resultado notificar(EventoReserva evento) {
        if (entregas.procesado(evento.eventId())) {
            return Resultado.DUPLICADO;
        }
        Preferencias canales = preferencias.find(evento.userId()).orElse(Preferencias.DEFAULTS);
        List<Canal> enviados = new ArrayList<>();
        Resultado resultado = Resultado.SIN_CANAL;
        if (canales.email()) {
            Optional<Contacto> contacto = contactos.contacto(evento.userId());
            if (contacto.isEmpty()) {
                resultado = Resultado.SIN_CUENTA;
            } else {
                String codigo = evento.tipo().plantilla(Canal.EMAIL);
                Plantilla plantilla = plantillas.find(codigo)
                        .orElseThrow(() -> new IllegalStateException("the template " + codigo + " is not seeded"));
                correo.enviar(contacto.get().email(), plantilla.render(evento.valores(contacto.get().nombre())));
                enviados.add(Canal.EMAIL);
                resultado = Resultado.ENVIADO;
            }
        }
        entregas.registrar(evento, enviados, clock.instant());
        return resultado;
    }
}
