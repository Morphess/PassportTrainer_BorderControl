package ua.passporttrainer.map;

import java.util.Objects;

/** Machine-readable provenance and integrity contract for the generated SVG. */
public record MapAssetMetadata(
        String dataset,
        String version,
        String tag,
        String viewpoint,
        String sourceSha256,
        String svgSha256,
        int regionCount,
        int subpathCount,
        int coordinatePairCount,
        long sizeBytes
) {
    public MapAssetMetadata {
        dataset = requireText(dataset, "dataset");
        version = requireText(version, "version");
        tag = requireText(tag, "tag");
        viewpoint = requireText(viewpoint, "viewpoint");
        sourceSha256 = requireHash(sourceSha256, "sourceSha256");
        svgSha256 = requireHash(svgSha256, "svgSha256");
        if (regionCount <= 0 || subpathCount <= 0 || coordinatePairCount <= 0 || sizeBytes <= 0) {
            throw new IllegalArgumentException("Map asset counts and size must be positive");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String requireHash(String value, String name) {
        String hash = requireText(value, name);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 value");
        }
        return hash;
    }
}
