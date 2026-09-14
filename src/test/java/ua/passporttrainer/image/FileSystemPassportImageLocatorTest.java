package ua.passporttrainer.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSystemPassportImageLocatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesNestedPortableRelativePathBelowConfiguredRoot() throws Exception {
        Path image = temporaryDirectory.resolve("Європа/Україна/Паспорт України.jpg");
        Files.createDirectories(image.getParent());
        Files.writeString(image, "image fixture");

        PassportImageLocator locator = new FileSystemPassportImageLocator(temporaryDirectory);

        assertEquals(image.toRealPath().toUri(), locator.locate("Європа/Україна/Паспорт України.jpg"));
    }

    @Test
    void rejectsAbsoluteTraversalAndNonPortablePaths() {
        PassportImageLocator locator = new FileSystemPassportImageLocator(temporaryDirectory);

        assertAll(
                () -> assertThrows(ImageLocationException.class, () -> locator.locate("/tmp/passport.jpg")),
                () -> assertThrows(ImageLocationException.class, () -> locator.locate("C:/passport.jpg")),
                () -> assertThrows(ImageLocationException.class, () -> locator.locate("../passport.jpg")),
                () -> assertThrows(ImageLocationException.class, () -> locator.locate("country/../passport.jpg")),
                () -> assertThrows(ImageLocationException.class, () -> locator.locate("country\\passport.jpg")),
                () -> assertThrows(ImageLocationException.class, () -> locator.locate("country//passport.jpg")),
                () -> assertThrows(ImageLocationException.class, () -> locator.locate(""))
        );
    }

    @Test
    void rejectsMissingFiles() {
        PassportImageLocator locator = new FileSystemPassportImageLocator(temporaryDirectory);

        assertThrows(ImageLocationException.class, () -> locator.locate("missing.jpg"));
    }

    @Test
    void rejectsSymbolicLinkEscapesWhenSupported() throws Exception {
        PassportImageLocator locator = new FileSystemPassportImageLocator(temporaryDirectory);
        Path externalFile = Files.createTempFile("passport-image-outside-root", ".jpg");
        try {
            try {
                Files.createSymbolicLink(temporaryDirectory.resolve("escape.jpg"), externalFile);
            } catch (IOException | UnsupportedOperationException | SecurityException exception) {
                assumeTrue(false, () -> "Symbolic links are unavailable: " + exception.getMessage());
            }
            assertThrows(ImageLocationException.class, () -> locator.locate("escape.jpg"));
        } finally {
            Files.deleteIfExists(externalFile);
        }
    }
}
