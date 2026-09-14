package ua.passporttrainer.manifest;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class CheckedInManifestTest {
    @Test
    void checkedInManifestMatchesImmutableSourceDataset() throws Exception {
        PassportManifestValidator.validate(
                Path.of("Зразки паспортних документів"),
                Path.of("data/passport-manifest")
        );
    }
}
