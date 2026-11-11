package co.edu.corhuila.property.notification.app;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The contract checks of Annex C that need neither a token nor a business endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationHttpTest {

    static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    MockMvc mvc;

    @Test
    void healthAnswersWithoutAToken() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    void anUnknownRouteAnswersWithTheEnvelopeAndTheCorrelationId() throws Exception {
        mvc.perform(get("/api/v1/nothing-here").header("X-Correlation-Id", "notif-404"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-Id", "notif-404"))
                .andExpect(jsonPath("$.error", is("NOT_FOUND")))
                .andExpect(jsonPath("$.traceId", is("notif-404")));
    }

    @Test
    void withoutACorrelationIdTheServiceGeneratesOne() throws Exception {
        mvc.perform(get("/api/v1/nothing-here"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.traceId", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void aCorrelationIdLongerThan128CharactersIsReplaced() throws Exception {
        mvc.perform(get("/health").header("X-Correlation-Id", "x".repeat(129)))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void aMethodTheRouteDoesNotHaveAnswersWithTheEnvelope() throws Exception {
        mvc.perform(post("/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }
}
