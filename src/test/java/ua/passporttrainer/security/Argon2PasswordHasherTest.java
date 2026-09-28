package ua.passporttrainer.security;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Argon2PasswordHasherTest {

    @Test
    void hashesWithArgon2idRandomSaltAndVerifiesOnlyTheCorrectPassword() {
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(1, 8_192, 1);
        char[] password = "correct horse battery staple".toCharArray();

        String first = hasher.hash(password);
        String second = hasher.hash(password);

        assertTrue(first.startsWith("$argon2id$v=19$m=8192,t=1,p=1$"));
        assertTrue(Base64.getDecoder().decode(first.split("\\$")[4]).length >= 16);
        assertFalse(first.equals(second));
        assertTrue(hasher.matches(password, first));
        assertFalse(hasher.matches("wrong password".toCharArray(), first));
    }

    @Test
    void detectsOutdatedAndMalformedEncodedHashes() {
        Argon2PasswordHasher originalPolicy = new Argon2PasswordHasher(1, 8_192, 1);
        Argon2PasswordHasher strongerPolicy = new Argon2PasswordHasher(2, 8_192, 1);
        String encoded = originalPolicy.hash("valid password".toCharArray());

        assertFalse(originalPolicy.needsRehash(encoded));
        assertTrue(strongerPolicy.needsRehash(encoded));
        assertFalse(strongerPolicy.matches("valid password".toCharArray(), "not-an-argon2-hash"));
        assertTrue(strongerPolicy.needsRehash("not-an-argon2-hash"));
    }
}
