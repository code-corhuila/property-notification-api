package co.edu.corhuila.property.notification.app;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Every route under /api/ validates the token by itself: the checks of Annex C, numeral 5.3.7. */
@SpringBootTest
@AutoConfigureMockMvc
class TokenValidationHttpTest {

    static final KeyPair OTHER_KEYS = generate();

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void publicKey(DynamicPropertyRegistry registry) {
        TestKeys.register(registry);
    }

    @Test
    void withoutATokenIs401WithTheEnvelope() throws Exception {
        mvc.perform(get("/api/v1/preferencias").header("X-Correlation-Id", "trace-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.traceId", is("trace-401")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"alg-none", "alg-hs256", "expired", "tampered", "other-key", "malformed"})
    void rejectsEveryInvalidToken(String kind) throws Exception {
        String token = switch (kind) {
            case "alg-none" -> token("none", 3600, TestKeys.KEYS.getPrivate());
            case "alg-hs256" -> token("HS256", 3600, TestKeys.KEYS.getPrivate());
            case "expired" -> token("RS256", -120, TestKeys.KEYS.getPrivate());
            case "tampered" -> token("RS256", 3600, TestKeys.KEYS.getPrivate()) + "x";
            case "other-key" -> token("RS256", 3600, OTHER_KEYS.getPrivate());
            default -> "not-a-jwt";
        };
        mvc.perform(get("/api/v1/preferencias").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    private static String token(String alg, long expiresIn, PrivateKey key) throws Exception {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString(("{\"alg\":\"" + alg + "\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"11111111-1111-4111-8111-111111111111\",\"roles\":[\"GUEST\"],\"exp\":"
                + (Instant.now().getEpochSecond() + expiresIn) + "}").getBytes(StandardCharsets.UTF_8));
        Signature rsa = Signature.getInstance("SHA256withRSA");
        rsa.initSign(key);
        rsa.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + enc.encodeToString(rsa.sign());
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
