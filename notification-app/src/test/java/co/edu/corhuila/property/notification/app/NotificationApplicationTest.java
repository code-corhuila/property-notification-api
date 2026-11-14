package co.edu.corhuila.property.notification.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The composition root starts: the three modules wire together. */
@SpringBootTest
class NotificationApplicationTest {

    @DynamicPropertySource
    static void publicKey(DynamicPropertyRegistry registry) {
        TestKeys.register(registry);
    }

    @Test
    void theApplicationStarts() {
        // Spring fails the test if the context does not start.
    }
}
