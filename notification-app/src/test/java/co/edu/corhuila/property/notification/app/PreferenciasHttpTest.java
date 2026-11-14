package co.edu.corhuila.property.notification.app;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** E-16 and E-17 over HTTP (HU-NOTIF-002), on the in-memory store. */
@SpringBootTest
@AutoConfigureMockMvc
class PreferenciasHttpTest {

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void publicKey(DynamicPropertyRegistry registry) {
        TestKeys.register(registry);
    }

    static String person(String role) {
        return TestKeys.bearer("\"sub\":\"" + UUID.randomUUID() + "\",\"roles\":[\"" + role + "\"]");
    }

    @Test
    void scenario1AUserWhoNeverSavedGetsTheDefaults() throws Exception {
        mvc.perform(get("/api/v1/preferencias").header("Authorization", person("GUEST")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"email\":true,\"sms\":false,\"push\":true}", true));
    }

    @Test
    void scenario2OnlyTheChannelSentChangesAndAllAreReturned() throws Exception {
        String token = person("GUEST");
        mvc.perform(patch("/api/v1/preferencias").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sms\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"email\":true,\"sms\":true,\"push\":true}", true));
        mvc.perform(patch("/api/v1/preferencias").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":false}"))
                .andExpect(content().json("{\"email\":false,\"sms\":true,\"push\":true}", true));
        mvc.perform(get("/api/v1/preferencias").header("Authorization", token))
                .andExpect(content().json("{\"email\":false,\"sms\":true,\"push\":true}", true));
    }

    @Test
    void eachUserSeesOnlyTheirOwn() throws Exception {
        mvc.perform(patch("/api/v1/preferencias").header("Authorization", person("HOST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"push\":false}"))
                .andExpect(jsonPath("$.push", is(false)));
        mvc.perform(get("/api/v1/preferencias").header("Authorization", person("ADMIN")))
                .andExpect(jsonPath("$.push", is(true)));
    }

    @Test
    void anEmptyChangeChangesNothing() throws Exception {
        mvc.perform(patch("/api/v1/preferencias").header("Authorization", person("GUEST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"email\":true,\"sms\":false,\"push\":true}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"sms\":\"yes\"}", "{\"email\":null}", "{\"push\":1}", "{\"fax\":true}"})
    void anInvalidChannelIs400NamingTheField(String body) throws Exception {
        String field = body.substring(2, body.indexOf('"', 2));
        mvc.perform(patch("/api/v1/preferencias").header("Authorization", person("GUEST"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details[*].field", containsInAnyOrder(field)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[true]", "not json"})
    void aBodyThatIsNotAnObjectIs400(String body) throws Exception {
        mvc.perform(patch("/api/v1/preferencias").header("Authorization", person("GUEST"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    void aServiceCredentialHasNoPreferences() throws Exception {
        String service = TestKeys.bearer("\"sub\":\"svc-workflow\",\"scope\":\"service\"");
        mvc.perform(get("/api/v1/preferencias").header("Authorization", service))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }
}
