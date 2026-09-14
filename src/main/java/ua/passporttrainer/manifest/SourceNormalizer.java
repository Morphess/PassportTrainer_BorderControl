package ua.passporttrainer.manifest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

/** Deterministic normalization used only for manifest lookup keys and identifiers. */
public final class SourceNormalizer {
    private SourceNormalizer() {
    }

    public static String lookupKey(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(normalized.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.appendCodePoint(codePoint);
            }
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    public static String normalizedSourcePath(String sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        return Normalizer.normalize(sourcePath.replace('\\', '/'), Normalizer.Form.NFC);
    }

    public static String relativeSourcePath(Path sourceRoot, Path sourcePath) {
        Path root = sourceRoot.toAbsolutePath().normalize();
        Path path = sourcePath.toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Source path is outside source root: " + sourcePath);
        }
        Path relative = root.relativize(path);
        StringJoiner joined = new StringJoiner("/");
        for (Path component : relative) {
            joined.add(component.toString());
        }
        return joined.toString();
    }

    public static String stableId(String prefix, String canonicalValue) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(canonicalValue, "canonicalValue");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    public static String displayName(String sourceName) {
        String value = Normalizer.normalize(sourceName, Normalizer.Form.NFC).strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Source name must not be blank");
        }
        int first = value.codePointAt(0);
        int firstLength = Character.charCount(first);
        return new String(Character.toChars(Character.toUpperCase(first))) + value.substring(firstLength);
    }
}
