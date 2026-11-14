package co.edu.corhuila.property.notification.application.port.in;

/** A valid credential without permission for the operation: 403 FORBIDDEN. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
