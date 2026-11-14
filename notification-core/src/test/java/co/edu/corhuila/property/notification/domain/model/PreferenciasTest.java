package co.edu.corhuila.property.notification.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PreferenciasTest {

    @Test
    void theDefaultsAreEmailAndPushOnAndSmsOff() {
        assertEquals(new Preferencias(true, false, true), Preferencias.DEFAULTS);
    }

    @Test
    void aPartialChangeKeepsTheChannelsThatAreNotSent() {
        assertEquals(new Preferencias(true, true, true), Preferencias.DEFAULTS.con(null, true, null));
        assertEquals(new Preferencias(false, false, false), Preferencias.DEFAULTS.con(false, false, false));
        assertEquals(Preferencias.DEFAULTS, Preferencias.DEFAULTS.con(null, null, null));
    }
}
