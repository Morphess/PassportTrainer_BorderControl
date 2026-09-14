package ua.passporttrainer;

import java.util.ResourceBundle;

import javafx.application.Application;
import org.junit.jupiter.api.Test;
import ua.passporttrainer.navigation.View;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassportTrainerApplicationTest {

    @Test
    void exposesExpectedInitialUiText() {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");

        assertEquals("PASSPORT TRAINER", messages.getString("app.title"));
        assertEquals(
                "Application initialized successfully",
                messages.getString("app.initializationMessage")
        );
    }

    @Test
    void initialViewIsAvailable() {
        assertNotNull(PassportTrainerApplication.class.getResource(View.START.fxmlPath()));
    }

    @Test
    void isAJavaFxApplication() {
        assertTrue(Application.class.isAssignableFrom(PassportTrainerApplication.class));
    }
}
