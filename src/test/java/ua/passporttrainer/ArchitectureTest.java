package ua.passporttrainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.EntityType;
import ua.passporttrainer.model.ReviewStatus;
import ua.passporttrainer.model.UserRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ArchitectureTest {

    private static final Path MODEL_SOURCE_DIRECTORY =
            Path.of("src", "main", "java", "ua", "passporttrainer", "model");

    @Test
    void modelLayerDoesNotImportJavaFx() throws IOException {
        try (var sources = Files.walk(MODEL_SOURCE_DIRECTORY)) {
            List<Path> javaSources = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            for (Path source : javaSources) {
                assertFalse(
                        Files.readString(source).contains("import javafx."),
                        () -> "JavaFX dependency found in model: " + source
                );
            }
        }
    }

    @Test
    void documentTypesMatchTheArchitectureContract() {
        assertEquals(
                List.of(
                        "ORDINARY_PASSPORT",
                        "DIPLOMATIC_PASSPORT",
                        "SERVICE_PASSPORT",
                        "NATIONAL_ID",
                        "UNKNOWN"
                ),
                List.of(DocumentType.values()).stream().map(Enum::name).toList()
        );
    }

    @Test
    void catalogueClassificationEnumsMatchThePersistedContract() {
        assertEquals(
                List.of(
                        "COUNTRY",
                        "TERRITORY",
                        "ORGANIZATION",
                        "DISPUTED_ENTITY",
                        "AMBIGUOUS_ENTITY"
                ),
                List.of(EntityType.values()).stream().map(Enum::name).toList()
        );
        assertEquals(
                List.of("VERIFIED", "AUTO_CLASSIFIED", "NEEDS_REVIEW", "EXCLUDED"),
                List.of(ReviewStatus.values()).stream().map(Enum::name).toList()
        );
    }

    @Test
    void userRolesMatchTheArchitectureContract() {
        assertEquals(List.of("USER", "ADMIN"),
                List.of(UserRole.values()).stream().map(Enum::name).toList());
    }
}
