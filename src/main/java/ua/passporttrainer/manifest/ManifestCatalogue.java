package ua.passporttrainer.manifest;

import ua.passporttrainer.model.DocumentType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** In-memory representation of the generated TSV passport catalogue. */
public record ManifestCatalogue(
        Summary summary,
        List<ContinentEntry> continents,
        List<EntityEntry> entities,
        List<DocumentEntry> documents,
        List<ImageEntry> images,
        List<AliasEntry> aliases
) {
    static final int SCHEMA_VERSION = 1;

    private static final List<String> MANIFEST_HEADER = List.of(
            "schemaVersion", "sourceRoot", "continentCount", "entityCount", "documentCount", "imageCount"
    );
    private static final List<String> CONTINENT_HEADER = List.of(
            "id", "code", "displayNameUk", "sourceName", "sortOrder"
    );
    private static final List<String> ENTITY_HEADER = List.of(
            "id", "primaryContinentId", "alternateContinentIds", "canonicalKey", "displayNameUk",
            "entityType", "sourceNames", "reviewStatus", "notes"
    );
    private static final List<String> DOCUMENT_HEADER = List.of(
            "id", "entityId", "documentType", "titleUk", "variant", "reviewStatus", "quizEligible",
            "duplicateOfDocumentId", "notes"
    );
    private static final List<String> IMAGE_HEADER = List.of(
            "id", "documentId", "sourcePath", "sourceFilename", "mimeType", "format", "width", "height",
            "fileSize", "sha256", "reviewStatus", "duplicateOfImageId", "notes"
    );
    private static final List<String> ALIAS_HEADER = List.of(
            "kind", "sourceValue", "canonicalValue", "targetId"
    );

    public ManifestCatalogue {
        continents = List.copyOf(continents);
        entities = List.copyOf(entities);
        documents = List.copyOf(documents);
        images = List.copyOf(images);
        aliases = List.copyOf(aliases);
    }

    public void write(Path outputDirectory) throws IOException {
        TsvTable.write(outputDirectory.resolve("manifest.tsv"), MANIFEST_HEADER, List.of(summary.values()));
        TsvTable.write(outputDirectory.resolve("continents.tsv"), CONTINENT_HEADER,
                continents.stream().map(ContinentEntry::values).toList());
        TsvTable.write(outputDirectory.resolve("entities.tsv"), ENTITY_HEADER,
                entities.stream().map(EntityEntry::values).toList());
        TsvTable.write(outputDirectory.resolve("documents.tsv"), DOCUMENT_HEADER,
                documents.stream().map(DocumentEntry::values).toList());
        TsvTable.write(outputDirectory.resolve("images.tsv"), IMAGE_HEADER,
                images.stream().map(ImageEntry::values).toList());
        TsvTable.write(outputDirectory.resolve("aliases.tsv"), ALIAS_HEADER,
                aliases.stream().map(AliasEntry::values).toList());
    }

    public static ManifestCatalogue read(Path manifestDirectory) throws IOException {
        TsvTable manifest = TsvTable.read(manifestDirectory.resolve("manifest.tsv"), MANIFEST_HEADER);
        if (manifest.rows().size() != 1) {
            throw new IllegalArgumentException("manifest.tsv must contain exactly one data row");
        }
        Summary summary = Summary.from(manifest.rows().getFirst());
        List<ContinentEntry> continents = readRows(
                manifestDirectory.resolve("continents.tsv"), CONTINENT_HEADER, ContinentEntry::from
        );
        List<EntityEntry> entities = readRows(
                manifestDirectory.resolve("entities.tsv"), ENTITY_HEADER, EntityEntry::from
        );
        List<DocumentEntry> documents = readRows(
                manifestDirectory.resolve("documents.tsv"), DOCUMENT_HEADER, DocumentEntry::from
        );
        List<ImageEntry> images = readRows(
                manifestDirectory.resolve("images.tsv"), IMAGE_HEADER, ImageEntry::from
        );
        List<AliasEntry> aliases = readRows(
                manifestDirectory.resolve("aliases.tsv"), ALIAS_HEADER, AliasEntry::from
        );
        return new ManifestCatalogue(summary, continents, entities, documents, images, aliases);
    }

    private static <T> List<T> readRows(
            Path path,
            List<String> header,
            RowReader<T> reader
    ) throws IOException {
        List<T> result = new ArrayList<>();
        for (Map<String, String> row : TsvTable.read(path, header).rows()) {
            result.add(reader.read(row));
        }
        return List.copyOf(result);
    }

    private static List<String> splitList(String value) {
        return value.isEmpty() ? List.of() : List.copyOf(Arrays.asList(value.split("\\|", -1)));
    }

    private static String joinList(List<String> values) {
        return String.join("|", values);
    }

    private static int integer(Map<String, String> row, String column) {
        try {
            return Integer.parseInt(TsvTable.required(row, column));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer in column " + column, exception);
        }
    }

    private static long longValue(Map<String, String> row, String column) {
        try {
            return Long.parseLong(TsvTable.required(row, column));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid long in column " + column, exception);
        }
    }

    private static boolean booleanValue(Map<String, String> row, String column) {
        return switch (TsvTable.required(row, column)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean in column " + column);
        };
    }

    public enum ReviewStatus {
        VERIFIED,
        AUTO_CLASSIFIED,
        NEEDS_REVIEW,
        EXCLUDED;

        public static ReviewStatus mostRestrictive(ReviewStatus first, ReviewStatus second) {
            return rank(first) >= rank(second) ? first : second;
        }

        private static int rank(ReviewStatus status) {
            return switch (status) {
                case VERIFIED -> 0;
                case AUTO_CLASSIFIED -> 1;
                case NEEDS_REVIEW -> 2;
                case EXCLUDED -> 3;
            };
        }
    }

    public enum EntityType {
        COUNTRY,
        TERRITORY,
        ORGANIZATION,
        DISPUTED_ENTITY,
        AMBIGUOUS_ENTITY
    }

    public enum AliasKind {
        CONTINENT,
        ENTITY,
        DOCUMENT_TYPE
    }

    public record Summary(
            int schemaVersion,
            String sourceRoot,
            int continentCount,
            int entityCount,
            int documentCount,
            int imageCount
    ) {
        List<String> values() {
            return List.of(
                    Integer.toString(schemaVersion), sourceRoot, Integer.toString(continentCount),
                    Integer.toString(entityCount), Integer.toString(documentCount), Integer.toString(imageCount)
            );
        }

        static Summary from(Map<String, String> row) {
            return new Summary(
                    integer(row, "schemaVersion"), TsvTable.required(row, "sourceRoot"),
                    integer(row, "continentCount"), integer(row, "entityCount"),
                    integer(row, "documentCount"), integer(row, "imageCount")
            );
        }
    }

    public record ContinentEntry(String id, String code, String displayNameUk, String sourceName, int sortOrder) {
        List<String> values() {
            return List.of(id, code, displayNameUk, sourceName, Integer.toString(sortOrder));
        }

        static ContinentEntry from(Map<String, String> row) {
            return new ContinentEntry(
                    TsvTable.required(row, "id"), TsvTable.required(row, "code"),
                    TsvTable.required(row, "displayNameUk"), TsvTable.required(row, "sourceName"),
                    integer(row, "sortOrder")
            );
        }
    }

    public record EntityEntry(
            String id,
            String primaryContinentId,
            List<String> alternateContinentIds,
            String canonicalKey,
            String displayNameUk,
            EntityType entityType,
            List<String> sourceNames,
            ReviewStatus reviewStatus,
            String notes
    ) {
        public EntityEntry {
            alternateContinentIds = List.copyOf(alternateContinentIds);
            sourceNames = List.copyOf(sourceNames);
        }

        List<String> values() {
            return List.of(
                    id, primaryContinentId, joinList(alternateContinentIds), canonicalKey, displayNameUk,
                    entityType.name(), joinList(sourceNames), reviewStatus.name(), notes
            );
        }

        static EntityEntry from(Map<String, String> row) {
            return new EntityEntry(
                    TsvTable.required(row, "id"), TsvTable.required(row, "primaryContinentId"),
                    splitList(row.get("alternateContinentIds")), TsvTable.required(row, "canonicalKey"),
                    TsvTable.required(row, "displayNameUk"), EntityType.valueOf(TsvTable.required(row, "entityType")),
                    splitList(TsvTable.required(row, "sourceNames")),
                    ReviewStatus.valueOf(TsvTable.required(row, "reviewStatus")), row.get("notes")
            );
        }
    }

    public record DocumentEntry(
            String id,
            String entityId,
            DocumentType documentType,
            String titleUk,
            String variant,
            ReviewStatus reviewStatus,
            boolean quizEligible,
            String duplicateOfDocumentId,
            String notes
    ) {
        List<String> values() {
            return List.of(
                    id, entityId, documentType.name(), titleUk, variant, reviewStatus.name(),
                    Boolean.toString(quizEligible), duplicateOfDocumentId, notes
            );
        }

        static DocumentEntry from(Map<String, String> row) {
            return new DocumentEntry(
                    TsvTable.required(row, "id"), TsvTable.required(row, "entityId"),
                    DocumentType.valueOf(TsvTable.required(row, "documentType")),
                    TsvTable.required(row, "titleUk"), row.get("variant"),
                    ReviewStatus.valueOf(TsvTable.required(row, "reviewStatus")),
                    booleanValue(row, "quizEligible"), row.get("duplicateOfDocumentId"), row.get("notes")
            );
        }
    }

    public record ImageEntry(
            String id,
            String documentId,
            String sourcePath,
            String sourceFilename,
            String mimeType,
            String format,
            int width,
            int height,
            long fileSize,
            String sha256,
            ReviewStatus reviewStatus,
            String duplicateOfImageId,
            String notes
    ) {
        List<String> values() {
            return List.of(
                    id, documentId, sourcePath, sourceFilename, mimeType, format,
                    Integer.toString(width), Integer.toString(height), Long.toString(fileSize), sha256,
                    reviewStatus.name(), duplicateOfImageId, notes
            );
        }

        static ImageEntry from(Map<String, String> row) {
            return new ImageEntry(
                    TsvTable.required(row, "id"), TsvTable.required(row, "documentId"),
                    TsvTable.required(row, "sourcePath"), TsvTable.required(row, "sourceFilename"),
                    TsvTable.required(row, "mimeType"), TsvTable.required(row, "format"),
                    integer(row, "width"), integer(row, "height"), longValue(row, "fileSize"),
                    TsvTable.required(row, "sha256"),
                    ReviewStatus.valueOf(TsvTable.required(row, "reviewStatus")),
                    row.get("duplicateOfImageId"), row.get("notes")
            );
        }
    }

    public record AliasEntry(AliasKind kind, String sourceValue, String canonicalValue, String targetId) {
        List<String> values() {
            return List.of(kind.name(), sourceValue, canonicalValue, targetId);
        }

        static AliasEntry from(Map<String, String> row) {
            return new AliasEntry(
                    AliasKind.valueOf(TsvTable.required(row, "kind")),
                    TsvTable.required(row, "sourceValue"), TsvTable.required(row, "canonicalValue"),
                    TsvTable.required(row, "targetId")
            );
        }
    }

    @FunctionalInterface
    private interface RowReader<T> {
        T read(Map<String, String> row);
    }
}
