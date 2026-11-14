package co.edu.corhuila.property.notification.application.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Preferences are own only: the account of the token, and only a person has one. */
class CallerTest {

    static final String ACCOUNT = "11111111-1111-4111-8111-111111111111";

    @Test
    void everyRoleOfAPersonNamesItsAccount() {
        for (String role : List.of("GUEST", "HOST", "ADMIN")) {
            assertEquals(UUID.fromString(ACCOUNT), new Caller(ACCOUNT, List.of(role), false).personId());
        }
    }

    @Test
    void aServiceCredentialHasNoPreferences() {
        assertThrows(AccessDeniedException.class, () -> new Caller("svc-workflow", List.of(), true).personId());
    }

    @Test
    void aTokenWithoutRolesHasNoPermission() {
        assertThrows(AccessDeniedException.class, () -> new Caller(ACCOUNT, List.of(), false).personId());
    }

    @Test
    void aPersonWhoseSubjectIsNotAnIdIsNotAuthenticated() {
        assertThrows(NotAuthenticatedException.class, () -> new Caller("alice", List.of("GUEST"), false).personId());
    }
}
