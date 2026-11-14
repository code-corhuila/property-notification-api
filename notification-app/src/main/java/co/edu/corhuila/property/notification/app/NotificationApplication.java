package co.edu.corhuila.property.notification.app;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication(scanBasePackages = "co.edu.corhuila.property.notification")
public class NotificationApplication {
    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--reservas-consumer")) {
            // The consumer of notification.reservas: the same jar, another process, without a web server.
            new SpringApplicationBuilder(ReservasConsumerApplication.class).web(WebApplicationType.NONE).run(args);
            return;
        }
        SpringApplication.run(NotificationApplication.class, args);
    }
}
