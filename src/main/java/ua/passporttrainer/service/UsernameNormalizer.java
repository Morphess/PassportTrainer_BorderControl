package ua.passporttrainer.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/** Produces the single canonical representation used for local login identity. */
public final class UsernameNormalizer {

    private UsernameNormalizer() {
    }

    public static String normalize(String username) {
        Objects.requireNonNull(username, "username");
        String canonical = Normalizer.normalize(username, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT);
        if (canonical.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return canonical;
    }
}
