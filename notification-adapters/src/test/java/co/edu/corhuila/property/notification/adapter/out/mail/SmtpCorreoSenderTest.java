package co.edu.corhuila.property.notification.adapter.out.mail;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.property.notification.domain.model.Plantilla.Mensaje;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** The SMTP conversation against a minimal fake server on a local port. */
class SmtpCorreoSenderTest {

    /** Accepts one message and returns everything the client wrote. */
    static CompletableFuture<String> fakeSmtp(ServerSocket server) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder seen = new StringBuilder();
            try (Socket s = server.accept();
                 var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                 var out = new PrintWriter(s.getOutputStream(), true)) {
                out.print("220 fake ESMTP\r\n");
                out.flush();
                boolean data = false;
                for (String line; (line = in.readLine()) != null; ) {
                    seen.append(line).append('\n');
                    if (data) {
                        if (line.equals(".")) {
                            data = false;
                            out.print("250 queued\r\n");
                            out.flush();
                        }
                        continue;
                    }
                    String verb = line.length() >= 4 ? line.substring(0, 4).toUpperCase() : line;
                    switch (verb) {
                        case "DATA" -> { data = true; out.print("354 go\r\n"); }
                        case "QUIT" -> { out.print("221 bye\r\n"); out.flush(); return seen.toString(); }
                        default -> out.print("250 ok\r\n");
                    }
                    out.flush();
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return seen.toString();
        });
    }

    @Test
    void sendsThePlainTextMessageInUtf8() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            var conversation = fakeSmtp(server);
            new SmtpCorreoSender("127.0.0.1", server.getLocalPort(), "", "", false, "no-reply@pms-property.test",
                    Duration.ofSeconds(5)).enviar("ana@example.com", new Mensaje("Tu reserva expiró", "Hola Ana: expiró."));
            String seen = conversation.get();
            assertTrue(seen.contains("MAIL FROM:<no-reply@pms-property.test>"), seen);
            assertTrue(seen.contains("RCPT TO:<ana@example.com>"), seen);
            assertTrue(seen.contains("Subject: =?UTF-8?"), seen);
            assertTrue(seen.contains("charset=UTF-8"), seen);
        }
    }

    @Test
    void aServerThatDoesNotAnswerIsAFailure() throws Exception {
        int closed;
        try (ServerSocket s = new ServerSocket(0)) {
            closed = s.getLocalPort();
        }
        var sender = new SmtpCorreoSender("127.0.0.1", closed, "", "", false, "no-reply@pms-property.test",
                Duration.ofSeconds(2));
        assertThrows(IllegalStateException.class, () -> sender.enviar("ana@example.com", new Mensaje("s", "b")));
    }
}
