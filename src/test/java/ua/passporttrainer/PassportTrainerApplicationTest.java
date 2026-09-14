package ua.passporttrainer;

import java.util.ResourceBundle;

import javafx.application.Application;
import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.EntityType;
import ua.passporttrainer.model.ReviewStatus;
import ua.passporttrainer.navigation.View;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassportTrainerApplicationTest {

    @Test
    void defaultResourcesCoverEveryCatalogueClassification() {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");

        for (DocumentType type : DocumentType.values()) {
            assertDoesNotThrow(() -> messages.getString("documentType." + type.name()));
        }
        for (EntityType type : EntityType.values()) {
            assertDoesNotThrow(() -> messages.getString("entityType." + type.name()));
        }
        for (ReviewStatus status : ReviewStatus.values()) {
            assertDoesNotThrow(() -> messages.getString("reviewStatus." + status.name()));
        }
    }

    @Test
    void initialViewIsAvailable() {
        for (View view : View.values()) {
            assertNotNull(
                    PassportTrainerApplication.class.getResource(view.fxmlPath()),
                    () -> "Missing FXML resource for " + view
            );
        }
    }

    @Test
    void isAJavaFxApplication() {
        assertTrue(Application.class.isAssignableFrom(PassportTrainerApplication.class));
    }
}
