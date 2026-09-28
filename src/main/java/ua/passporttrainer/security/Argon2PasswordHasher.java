package ua.passporttrainer.security;

import java.util.Arrays;
import java.util.Objects;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

/** Argon2id password hashing with self-describing encoded hashes. */
public final class Argon2PasswordHasher implements PasswordHasher {

    private static final int DEFAULT_ITERATIONS = 3;
    private static final int DEFAULT_MEMORY_KIB = 65_536;
    private static final int DEFAULT_PARALLELISM = 1;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;

    private final Argon2 argon2;
    private final int iterations;
    private final int memoryKiB;
    private final int parallelism;

    public Argon2PasswordHasher() {
        this(DEFAULT_ITERATIONS, DEFAULT_MEMORY_KIB, DEFAULT_PARALLELISM);
    }

    Argon2PasswordHasher(int iterations, int memoryKiB, int parallelism) {
        if (iterations <= 0 || memoryKiB <= 0 || parallelism <= 0) {
            throw new IllegalArgumentException("Argon2 parameters must be positive");
        }
        this.iterations = iterations;
        this.memoryKiB = memoryKiB;
        this.parallelism = parallelism;
        argon2 = Argon2Factory.create(
                Argon2Factory.Argon2Types.ARGON2id,
                SALT_LENGTH_BYTES,
                HASH_LENGTH_BYTES
        );
    }

    @Override
    public String hash(char[] rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        char[] passwordCopy = rawPassword.clone();
        try {
            return argon2.hash(iterations, memoryKiB, parallelism, passwordCopy);
        } finally {
            Arrays.fill(passwordCopy, '\0');
        }
    }

    @Override
    public boolean matches(char[] rawPassword, String encodedHash) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(encodedHash, "encodedHash");
        char[] passwordCopy = rawPassword.clone();
        try {
            return argon2.verify(encodedHash, passwordCopy);
        } catch (IllegalArgumentException exception) {
            return false;
        } finally {
            Arrays.fill(passwordCopy, '\0');
        }
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        Objects.requireNonNull(encodedHash, "encodedHash");
        if (!encodedHash.startsWith("$argon2id$v=19$")) {
            return true;
        }
        try {
            return argon2.needsRehash(encodedHash, iterations, memoryKiB, parallelism);
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }
}
