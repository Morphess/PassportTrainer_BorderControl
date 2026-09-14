package ua.passporttrainer.manifest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SourceNormalizerTest {
    @Test
    void normalizesUnicodeCaseAndWhitespace() {
        assertEquals("україна", SourceNormalizer.lookupKey("  УКРАЇНА\u00a0  "));
    }

    @Test
    void normalizesPlatformSeparatorsWithoutUsingInstallationPath() {
        String linux = SourceNormalizer.normalizedSourcePath("Європа/Україна/ззп.jpg");
        String windows = SourceNormalizer.normalizedSourcePath("Європа\\Україна\\ззп.jpg");

        assertEquals("Європа/Україна/ззп.jpg", linux);
        assertEquals(linux, windows);
        assertEquals(
                SourceNormalizer.stableId("image:", linux),
                SourceNormalizer.stableId("image:", windows)
        );
        assertNotEquals(
                SourceNormalizer.stableId("image:", linux),
                SourceNormalizer.stableId("document:", linux)
        );
    }
}
