package ua.passporttrainer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsernameNormalizerTest {

    @Test
    void appliesNfkcTrimmingAndLocaleIndependentLowercase() {
        assertEquals("alice", UsernameNormalizer.normalize("  ＡLICE  "));
        assertEquals("école", UsernameNormalizer.normalize(" E\u0301COLE "));
    }

    @Test
    void rejectsBlankCanonicalUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UsernameNormalizer.normalize(" \t\n ")
        );
    }
}
