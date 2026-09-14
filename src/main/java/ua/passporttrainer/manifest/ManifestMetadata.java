package ua.passporttrainer.manifest;

import ua.passporttrainer.manifest.ManifestCatalogue.EntityType;
import ua.passporttrainer.manifest.ManifestCatalogue.ReviewStatus;
import ua.passporttrainer.model.DocumentType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ManifestMetadata {
    private static final List<String> CONTINENT_HEADER = List.of(
            "sourceKey", "id", "code", "displayNameUk", "sourceName", "sortOrder"
    );
    private static final List<String> ENTITY_HEADER = List.of(
            "sourceKey", "canonicalKey", "displayNameUk", "entityType", "primaryContinentId",
            "alternateContinentIds", "reviewStatus", "notes"
    );
    private static final List<String> DOCUMENT_TYPE_HEADER = List.of("sourceToken", "documentType");
    private static final List<String> SOURCE_OVERRIDE_HEADER = List.of(
            "sourcePath", "canonicalEntityKey", "documentType", "reviewStatus", "quizEligible", "notes"
    );
    private static final List<String> SOURCE_EXCLUSION_HEADER = List.of("sourcePath", "reviewStatus", "notes");

    private final List<ContinentRule> continents;
    private final Map<String, ContinentRule> continentsBySourceKey;
    private final Map<String, EntityCorrection> entityCorrections;
    private final Map<String, DocumentType> documentTypes;
    private final Map<String, SourceOverride> sourceOverrides;
    private final Map<String, SourceExclusion> sourceExclusions;

    private ManifestMetadata(
            List<ContinentRule> continents,
            Map<String, ContinentRule> continentsBySourceKey,
            Map<String, EntityCorrection> entityCorrections,
            Map<String, DocumentType> documentTypes,
            Map<String, SourceOverride> sourceOverrides,
            Map<String, SourceExclusion> sourceExclusions
    ) {
        this.continents = List.copyOf(continents);
        this.continentsBySourceKey = Map.copyOf(continentsBySourceKey);
        this.entityCorrections = Map.copyOf(entityCorrections);
        this.documentTypes = Map.copyOf(documentTypes);
        this.sourceOverrides = Map.copyOf(sourceOverrides);
        this.sourceExclusions = Map.copyOf(sourceExclusions);
    }

    static ManifestMetadata load(Path metadataDirectory) throws IOException {
        List<ContinentRule> continents = new ArrayList<>();
        Map<String, ContinentRule> continentsBySourceKey = new LinkedHashMap<>();
        for (Map<String, String> row : TsvTable.read(
                metadataDirectory.resolve("continents.tsv"), CONTINENT_HEADER
        ).rows()) {
            ContinentRule rule = new ContinentRule(
                    SourceNormalizer.lookupKey(TsvTable.required(row, "sourceKey")),
                    TsvTable.required(row, "id"), TsvTable.required(row, "code"),
                    TsvTable.required(row, "displayNameUk"), TsvTable.required(row, "sourceName"),
                    parsePositiveInt(row, "sortOrder")
            );
            putUnique(continentsBySourceKey, rule.sourceKey(), rule, "continent source key");
            continents.add(rule);
        }

        Map<String, EntityCorrection> entityCorrections = new LinkedHashMap<>();
        for (Map<String, String> row : TsvTable.read(
                metadataDirectory.resolve("entity-corrections.tsv"), ENTITY_HEADER
        ).rows()) {
            String sourceKey = SourceNormalizer.lookupKey(TsvTable.required(row, "sourceKey"));
            EntityCorrection correction = new EntityCorrection(
                    sourceKey,
                    optionalLookupKey(row.get("canonicalKey")),
                    row.get("displayNameUk"),
                    optionalEnum(EntityType.class, row.get("entityType")),
                    row.get("primaryContinentId"),
                    splitList(row.get("alternateContinentIds")),
                    optionalEnum(ReviewStatus.class, row.get("reviewStatus")),
                    row.get("notes")
            );
            putUnique(entityCorrections, sourceKey, correction, "entity source key");
        }

        Map<String, DocumentType> documentTypes = new LinkedHashMap<>();
        for (Map<String, String> row : TsvTable.read(
                metadataDirectory.resolve("document-type-aliases.tsv"), DOCUMENT_TYPE_HEADER
        ).rows()) {
            String token = SourceNormalizer.lookupKey(TsvTable.required(row, "sourceToken"));
            DocumentType type = DocumentType.valueOf(TsvTable.required(row, "documentType"));
            if (type == DocumentType.UNKNOWN) {
                throw new IllegalArgumentException("UNKNOWN must result from an unresolved token, not an alias");
            }
            putUnique(documentTypes, token, type, "document type alias");
        }

        Map<String, SourceOverride> sourceOverrides = new LinkedHashMap<>();
        for (Map<String, String> row : TsvTable.read(
                metadataDirectory.resolve("source-overrides.tsv"), SOURCE_OVERRIDE_HEADER
        ).rows()) {
            String sourcePath = normalizedPath(TsvTable.required(row, "sourcePath"));
            SourceOverride override = new SourceOverride(
                    sourcePath,
                    optionalLookupKey(row.get("canonicalEntityKey")),
                    optionalEnum(DocumentType.class, row.get("documentType")),
                    optionalEnum(ReviewStatus.class, row.get("reviewStatus")),
                    optionalBoolean(row.get("quizEligible")),
                    row.get("notes")
            );
            putUnique(sourceOverrides, sourcePath, override, "source override path");
        }

        Map<String, SourceExclusion> sourceExclusions = new LinkedHashMap<>();
        for (Map<String, String> row : TsvTable.read(
                metadataDirectory.resolve("source-exclusions.tsv"), SOURCE_EXCLUSION_HEADER
        ).rows()) {
            String sourcePath = normalizedPath(TsvTable.required(row, "sourcePath"));
            ReviewStatus reviewStatus = ReviewStatus.valueOf(TsvTable.required(row, "reviewStatus"));
            if (reviewStatus != ReviewStatus.EXCLUDED) {
                throw new IllegalArgumentException("Source exclusions must use EXCLUDED: " + sourcePath);
            }
            SourceExclusion exclusion = new SourceExclusion(sourcePath, reviewStatus, row.get("notes"));
            putUnique(sourceExclusions, sourcePath, exclusion, "source exclusion path");
        }

        return new ManifestMetadata(
                continents, continentsBySourceKey, entityCorrections, documentTypes, sourceOverrides, sourceExclusions
        );
    }

    private static int parsePositiveInt(Map<String, String> row, String column) {
        try {
            int value = Integer.parseInt(TsvTable.required(row, column));
            if (value <= 0) {
                throw new IllegalArgumentException(column + " must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer in column " + column, exception);
        }
    }

    private static String normalizedPath(String sourcePath) {
        String normalized = SourceNormalizer.normalizedSourcePath(sourcePath);
        if (normalized.startsWith("/") || normalized.equals("..") || normalized.startsWith("../")
                || normalized.contains("/../") || normalized.endsWith("/..")) {
            throw new IllegalArgumentException("Source path must be relative and contained: " + sourcePath);
        }
        return normalized;
    }

    private static String optionalLookupKey(String value) {
        return value == null || value.isBlank() ? null : SourceNormalizer.lookupKey(value);
    }

    private static List<String> splitList(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\\|", -1));
    }

    private static Boolean optionalBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Invalid optional boolean: " + value);
        };
    }

    private static <E extends Enum<E>> E optionalEnum(Class<E> type, String value) {
        return value == null || value.isBlank() ? null : Enum.valueOf(type, value);
    }

    private static <K, V> void putUnique(Map<K, V> values, K key, V value, String description) {
        if (values.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("Duplicate " + description + ": " + key);
        }
    }

    List<ContinentRule> continents() {
        return continents;
    }

    ContinentRule continent(String sourceKey) {
        ContinentRule rule = continentsBySourceKey.get(sourceKey);
        if (rule == null) {
            throw new IllegalArgumentException("Unmapped continent source key: " + sourceKey);
        }
        return rule;
    }

    EntityCorrection entityCorrection(String sourceKey) {
        return entityCorrections.get(sourceKey);
    }

    Map<String, EntityCorrection> entityCorrections() {
        return entityCorrections;
    }

    DocumentType documentType(String sourceToken) {
        return documentTypes.getOrDefault(sourceToken, DocumentType.UNKNOWN);
    }

    Map<String, DocumentType> documentTypes() {
        return documentTypes;
    }

    SourceOverride sourceOverride(String normalizedSourcePath) {
        return sourceOverrides.get(normalizedSourcePath);
    }

    Map<String, SourceOverride> sourceOverrides() {
        return sourceOverrides;
    }

    Map<String, SourceExclusion> sourceExclusions() {
        return sourceExclusions;
    }

    record ContinentRule(
            String sourceKey,
            String id,
            String code,
            String displayNameUk,
            String sourceName,
            int sortOrder
    ) {
    }

    record EntityCorrection(
            String sourceKey,
            String canonicalKey,
            String displayNameUk,
            EntityType entityType,
            String primaryContinentId,
            List<String> alternateContinentIds,
            ReviewStatus reviewStatus,
            String notes
    ) {
        EntityCorrection {
            alternateContinentIds = List.copyOf(alternateContinentIds);
        }
    }

    record SourceOverride(
            String sourcePath,
            String canonicalEntityKey,
            DocumentType documentType,
            ReviewStatus reviewStatus,
            Boolean quizEligible,
            String notes
    ) {
    }

    record SourceExclusion(String sourcePath, ReviewStatus reviewStatus, String notes) {
    }
}
