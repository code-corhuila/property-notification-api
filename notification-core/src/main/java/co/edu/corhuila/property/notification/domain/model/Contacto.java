package co.edu.corhuila.property.notification.domain.model;

/** The address of a guest, read from Identity (E-26) when sending and never stored (DEC-005). */
public record Contacto(String email, String nombre) {

    @Override
    public String toString() {
        return "Contacto[***]";    // never in a log
    }
}
