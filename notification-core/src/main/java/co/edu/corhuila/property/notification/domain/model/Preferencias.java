package co.edu.corhuila.property.notification.domain.model;

/**
 * The channels a user accepts (HU-NOTIF-002, 06-data/models.md). A user who never
 * saved preferences has the defaults: e-mail and push on, SMS off.
 */
public record Preferencias(boolean email, boolean sms, boolean push) {

    public static final Preferencias DEFAULTS = new Preferencias(true, false, true);

    /** A partial change: a channel that is not sent (null) keeps its value. */
    public Preferencias con(Boolean email, Boolean sms, Boolean push) {
        return new Preferencias(email != null ? email : this.email,
                sms != null ? sms : this.sms,
                push != null ? push : this.push);
    }
}
