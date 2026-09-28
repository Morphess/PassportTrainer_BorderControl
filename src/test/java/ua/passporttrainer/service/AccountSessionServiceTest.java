package ua.passporttrainer.service;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.database.ApplicationDatabaseContext;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.security.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountSessionServiceTest {

    private static final char[] PASSWORD = "correct password".toCharArray();

    @TempDir
    Path temporaryDirectory;

    @Test
    void firstRunCreatesOneAdminThenOnlyOrdinaryUserAccounts() throws Exception {
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(
                temporaryDirectory.resolve("first-run.sqlite")
        );
        AccountSessionService access = context.accountSessionService();

        assertTrue(access.initialAdministratorRequired());

        AuthenticatedUser administrator = access.createInitialAdministratorAndLogin(
                "  ＡDMIN  ", "Administrator", PASSWORD, PASSWORD
        );

        assertEquals(UserRole.ADMIN, administrator.role());
        assertEquals("admin", administrator.username());
        assertEquals(administrator, access.currentUser().orElseThrow());
        assertFalse(access.initialAdministratorRequired());
        assertThrows(
                InitialAdministratorAlreadyExistsException.class,
                () -> access.createInitialAdministratorAndLogin(
                        "second-admin", "Second", PASSWORD, PASSWORD
                )
        );

        AuthenticatedUser ordinary = access.createOrdinaryAccountAndLogin(
                "  ＵSER  ", "Ordinary User", PASSWORD, PASSWORD
        );

        assertEquals(UserRole.USER, ordinary.role());
        assertEquals("user", ordinary.username());
        assertEquals(UserRole.USER, context.userRepository()
                .findById(ordinary.id()).orElseThrow().role());
        assertThrows(
                UsernameAlreadyExistsException.class,
                () -> access.createOrdinaryAccountAndLogin(
                        " user ", "Duplicate", PASSWORD, PASSWORD
                )
        );
        assertThrows(
                PasswordConfirmationException.class,
                () -> access.createOrdinaryAccountAndLogin(
                        "different", "Different", PASSWORD, "not the same".toCharArray()
                )
        );
    }

    @Test
    void loginAnonymousLogoutSwitchAndInactiveRejectionUpdateOnlyCurrentSession() throws Exception {
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(
                temporaryDirectory.resolve("session-access.sqlite")
        );
        AccountSessionService access = context.accountSessionService();
        AuthenticatedUser administrator = access.createInitialAdministratorAndLogin(
                "admin", "Administrator", PASSWORD, PASSWORD
        );
        AuthenticatedUser ordinary = access.createOrdinaryAccountAndLogin(
                "alice", "Alice", PASSWORD, PASSWORD
        );

        access.logout();
        assertTrue(access.currentUser().isEmpty());
        assertTrue(access.login("  ＡLICE ", PASSWORD));
        assertEquals(ordinary, access.currentUser().orElseThrow());

        access.switchUser();
        assertTrue(access.currentUser().isEmpty());
        assertFalse(access.login("alice", "wrong password".toCharArray()));
        assertTrue(access.currentUser().isEmpty());

        assertTrue(access.login("admin", PASSWORD));
        assertEquals(administrator, access.currentUser().orElseThrow());
        access.continueAnonymously();
        assertTrue(access.currentUser().isEmpty());

        context.userAccountService().deactivate(ordinary.id());
        assertFalse(access.login("alice", PASSWORD));
        assertTrue(access.currentUser().isEmpty());
    }
}
