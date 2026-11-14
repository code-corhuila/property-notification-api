package co.edu.corhuila.property.notification.adapter.out.mail;

import co.edu.corhuila.property.notification.application.port.out.CorreoSender;
import co.edu.corhuila.property.notification.domain.model.Plantilla.Mensaje;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Properties;

/**
 * The e-mail channel (DEC-006): SMTP to a host set by configuration. In develop, the test mail
 * server of --profile dev; in qa and main, the relay of the environment, with STARTTLS and its
 * credentials as secrets. Plain text in UTF-8. The address and the body are never logged.
 */
public class SmtpCorreoSender implements CorreoSender {

    private final Session session;
    private final InternetAddress from;

    public SmtpCorreoSender(String host, int port, String user, String password, boolean starttls, String from,
                            Duration timeout) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeout.toMillis()));
        props.put("mail.smtp.timeout", String.valueOf(timeout.toMillis()));
        props.put("mail.smtp.writetimeout", String.valueOf(timeout.toMillis()));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.starttls.required", String.valueOf(starttls));
        boolean auth = user != null && !user.isBlank();
        props.put("mail.smtp.auth", String.valueOf(auth));
        this.session = auth ? Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        }) : Session.getInstance(props);
        try {
            this.from = new InternetAddress(from, true);
        } catch (MessagingException e) {
            throw new IllegalArgumentException("MAIL_FROM is not an e-mail address", e);
        }
    }

    @Override
    public void enviar(String para, Mensaje mensaje) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(from);
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(para, true));
            message.setSubject(mensaje.asunto(), "UTF-8");
            message.setText(mensaje.cuerpo(), "UTF-8");
            Transport.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("the mail server did not accept the message: " + e.getClass().getSimpleName(), e);
        }
    }
}
