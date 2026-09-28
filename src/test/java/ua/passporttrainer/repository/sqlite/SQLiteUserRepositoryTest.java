package ua.passporttrainer.repository.sqlite;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.database.ApplicationDatabaseContext;
import ua.passporttrainer.model.NewUser;
import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.repository.RepositoryAccessException;
import ua.passporttrainer.security.AuthenticatedUser;
import ua.passporttrainer.security.PasswordHasher;
import ua.passporttrainer.service.UserAccountService;
import ua.passporttrainer.service.UsernameAlreadyExistsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteUserRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsUpdatesAndSoftDeactivatesCanonicalLocalUser() throws Exception {
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(
                temporaryDirectory.resolve("users.sqlite")
        );
        UserAccountService accounts = new UserAccountService(
                context.userRepository(), new FixedPasswordHasher(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        accounts.createInitialAdministrator(
                "admin", "Administrator", "password".toCharArray(), "password".toCharArray()
        );
        AuthenticatedUser principal = accounts.createOrdinaryAccount(
                "  ＡLICE  ", "  Alice Example  ",
                "password".toCharArray(), "password".toCharArray()
        );
        User created = context.userRepository().findById(principal.id()).orElseThrow();
        User renamed = accounts.updateDisplayName(created.id(), "  Alice Updated  ");
        User deactivated = accounts.deactivate(created.id());
        User persisted = context.userRepository().findById(created.id()).orElseThrow();

        assertTrue(created.id() > 0);
        assertEquals("alice", created.username());
        assertEquals("Alice Example", created.displayName());
        assertEquals(NOW, created.createdAt());
        assertEquals(NOW, created.updatedAt());
        assertEquals("Alice Updated", renamed.displayName());
        assertFalse(deactivated.active());
        assertEquals(deactivated, persisted);
        assertEquals(deactivated, context.userRepository()
                .findByUsername("alice").orElseThrow());
    }

    @Test
    void rejectsDuplicateCanonicalUsernameInServiceAndDatabase() throws Exception {
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(
                temporaryDirectory.resolve("duplicates.sqlite")
        );
        UserAccountService accounts = new UserAccountService(
                context.userRepository(), new FixedPasswordHasher(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        accounts.createInitialAdministrator(
                "admin", "Administrator", "password".toCharArray(), "password".toCharArray()
        );
        accounts.createOrdinaryAccount(
                "Ａlice", "First", "password".toCharArray(), "password".toCharArray()
        );

        assertThrows(
                UsernameAlreadyExistsException.class,
                () -> accounts.createOrdinaryAccount(
                        " ALICE ", "Second",
                        "password".toCharArray(), "password".toCharArray()
                )
        );
        assertThrows(
                RepositoryAccessException.class,
                () -> context.userRepository().create(new NewUser(
                        "alice", "Direct duplicate", "encoded-test-hash",
                        UserRole.USER, NOW
                ))
        );
    }

    private static final class FixedPasswordHasher implements PasswordHasher {

        @Override
        public String hash(char[] rawPassword) {
            return "encoded-test-hash";
        }

        @Override
        public boolean matches(char[] rawPassword, String encodedHash) {
            return false;
        }

        @Override
        public boolean needsRehash(String encodedHash) {
            return false;
        }
    }
}
