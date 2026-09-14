package ua.passporttrainer.manifest;

import ua.passporttrainer.manifest.ManifestCatalogue.AliasEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.AliasKind;
import ua.passporttrainer.manifest.ManifestCatalogue.ContinentEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.DocumentEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.EntityEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.ImageEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.ReviewStatus;
import ua.passporttrainer.model.DocumentType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/** Validates generated catalogue references, invariants, ordering, and source traceability. */
public final class PassportManifestValidator {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Comparator<String> SOURCE_PATH_ORDER = Comparator
            .comparing(SourceNormalizer::normalizedSourcePath)
            .thenComparing(Function.identity());

    private PassportManifestValidator() {
    }

    public static void validate(Path sourceRoot, Path manifestDirectory) throws IOException {
        validate(sourceRoot, ManifestCatalogue.read(manifestDirectory));
    }

    public static void validate(Path sourceRoot, ManifestCatalogue catalogue) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(catalogue, "catalogue");
        Path source = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Source root is not a directory: " + sourceRoot);
        }
        validateSummary(source, catalogue);

        Map<String, ContinentEntry> continents = uniqueById(catalogue.continents(), ContinentEntry::id, "continent");
        Map<String, EntityEntry> entities = uniqueById(catalogue.entities(), EntityEntry::id, "entity");
        Map<String, DocumentEntry> documents = uniqueById(catalogue.documents(), DocumentEntry::id, "document");
        Map<String, ImageEntry> images = uniqueById(catalogue.images(), ImageEntry::id, "image");
        requireSorted(
                catalogue.continents(), Comparator.comparingInt(ContinentEntry::sortOrder).thenComparing(ContinentEntry::id),
                "continents"
        );
        requireSorted(catalogue.entities(), Comparator.comparing(EntityEntry::id), "entities");
        requireSorted(catalogue.documents(), Comparator.comparing(DocumentEntry::id), "documents");
        requireSorted(
                catalogue.images(), Comparator.comparing(ImageEntry::sourcePath, SOURCE_PATH_ORDER), "images"
        );
        requireSorted(
                catalogue.aliases(), Comparator.comparing((AliasEntry alias) -> alias.kind().name())
                        .thenComparing(AliasEntry::sourceValue).thenComparing(AliasEntry::targetId),
                "aliases"
        );

        validateContinents(continents);
        validateEntities(entities, continents);
        validateDocuments(documents, entities);
        Map<String, ImageEntry> imagesByDocument = validateImages(source, images, documents);
        validateCompleteSourceCoverage(source, images.values());
        validateDuplicates(images.values(), imagesByDocument, documents);
        validateAliases(catalogue.aliases(), continents, entities);
    }

    private static void validateSummary(Path sourceRoot, ManifestCatalogue catalogue) {
        if (catalogue.summary().schemaVersion() != ManifestCatalogue.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported manifest schema version");
        }
        if (!catalogue.summary().sourceRoot().equals(sourceRoot.getFileName().toString())) {
            throw new IllegalArgumentException("Manifest source root does not match the dataset directory");
        }
        if (catalogue.summary().continentCount() != catalogue.continents().size()
                || catalogue.summary().entityCount() != catalogue.entities().size()
                || catalogue.summary().documentCount() != catalogue.documents().size()
                || catalogue.summary().imageCount() != catalogue.images().size()) {
            throw new IllegalArgumentException("Manifest summary counts do not match table contents");
        }
    }

    private static void validateContinents(Map<String, ContinentEntry> continents) {
        Set<String> codes = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (ContinentEntry continent : continents.values()) {
            if (!codes.add(continent.code())) {
                throw new IllegalArgumentException("Duplicate continent code: " + continent.code());
            }
            if (continent.sortOrder() <= 0 || !sortOrders.add(continent.sortOrder())) {
                throw new IllegalArgumentException("Invalid or duplicate continent sort order: " + continent.sortOrder());
            }
        }
    }

    private static void validateEntities(
            Map<String, EntityEntry> entities,
            Map<String, ContinentEntry> continents
    ) {
        Set<String> canonicalKeys = new HashSet<>();
        for (EntityEntry entity : entities.values()) {
            if (!entity.id().equals(SourceNormalizer.stableId("entity:", entity.canonicalKey()))) {
                throw new IllegalArgumentException("Entity ID is not deterministic: " + entity.id());
            }
            if (!entity.canonicalKey().equals(SourceNormalizer.lookupKey(entity.canonicalKey()))) {
                throw new IllegalArgumentException("Entity canonical key is not normalized: " + entity.canonicalKey());
            }
            if (!canonicalKeys.add(entity.canonicalKey())) {
                throw new IllegalArgumentException("Duplicate entity canonical key: " + entity.canonicalKey());
            }
            requireReference(continents, entity.primaryContinentId(), "entity primary continent");
            Set<String> alternateIds = new HashSet<>();
            for (String continentId : entity.alternateContinentIds()) {
                requireReference(continents, continentId, "entity alternate continent");
                if (continentId.equals(entity.primaryContinentId()) || !alternateIds.add(continentId)) {
                    throw new IllegalArgumentException("Invalid alternate continent for entity: " + entity.id());
                }
            }
            if (entity.sourceNames().isEmpty() || entity.sourceNames().stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("Entity must retain at least one source name: " + entity.id());
            }
        }
    }

    private static void validateDocuments(
            Map<String, DocumentEntry> documents,
            Map<String, EntityEntry> entities
    ) {
        for (DocumentEntry document : documents.values()) {
            EntityEntry entity = requireReference(entities, document.entityId(), "document entity");
            if (document.documentType() == DocumentType.UNKNOWN
                    && document.reviewStatus() != ReviewStatus.NEEDS_REVIEW
                    && document.reviewStatus() != ReviewStatus.EXCLUDED) {
                throw new IllegalArgumentException("UNKNOWN document must require review: " + document.id());
            }
            if (document.quizEligible()) {
                if (document.documentType() == DocumentType.UNKNOWN
                        || document.reviewStatus() != ReviewStatus.VERIFIED
                        || entity.reviewStatus() != ReviewStatus.VERIFIED) {
                    throw new IllegalArgumentException("Quiz-eligible document is unresolved: " + document.id());
                }
            }
        }
    }

    private static Map<String, ImageEntry> validateImages(
            Path sourceRoot,
            Map<String, ImageEntry> images,
            Map<String, DocumentEntry> documents
    ) throws IOException {
        Map<String, ImageEntry> byDocument = new HashMap<>();
        Set<String> sourcePaths = new HashSet<>();
        for (ImageEntry image : images.values()) {
            DocumentEntry document = requireReference(documents, image.documentId(), "image document");
            if (byDocument.putIfAbsent(image.documentId(), image) != null) {
                throw new IllegalArgumentException("Stage 5 document has more than one source image: " + image.documentId());
            }
            if (!sourcePaths.add(SourceNormalizer.normalizedSourcePath(image.sourcePath()))) {
                throw new IllegalArgumentException("Duplicate normalized source path: " + image.sourcePath());
            }
            if (image.sourcePath().contains("\\")) {
                throw new IllegalArgumentException("Source path must use POSIX separators: " + image.sourcePath());
            }
            String normalizedPath = SourceNormalizer.normalizedSourcePath(image.sourcePath());
            String expectedImageId = SourceNormalizer.stableId("image:", normalizedPath);
            String expectedDocumentId = SourceNormalizer.stableId("document:", normalizedPath);
            if (!image.id().equals(expectedImageId) || !document.id().equals(expectedDocumentId)) {
                throw new IllegalArgumentException("Source path does not map to deterministic IDs: " + image.sourcePath());
            }
            Path sourcePath = resolveContained(sourceRoot, image.sourcePath());
            if (!Files.isRegularFile(sourcePath)) {
                throw new IllegalArgumentException("Manifest source path does not exist: " + image.sourcePath());
            }
            if (!sourcePath.getFileName().toString().equals(image.sourceFilename())) {
                throw new IllegalArgumentException("Source filename does not match path: " + image.sourcePath());
            }
            if (Files.size(sourcePath) != image.fileSize()) {
                throw new IllegalArgumentException("Source size changed: " + image.sourcePath());
            }
            if (!sha256(sourcePath).equals(image.sha256())) {
                throw new IllegalArgumentException("Source SHA-256 changed: " + image.sourcePath());
            }
            if (image.width() <= 0 || image.height() <= 0) {
                throw new IllegalArgumentException("Invalid image dimensions: " + image.sourcePath());
            }
            String extension = extension(sourcePath);
            boolean expectedPng = extension.equals("png");
            if ((expectedPng && (!image.format().equals("PNG") || !image.mimeType().equals("image/png")))
                    || (!expectedPng && (!image.format().equals("JPEG") || !image.mimeType().equals("image/jpeg")))) {
                throw new IllegalArgumentException("Image format metadata does not match source path: " + image.sourcePath());
            }
            if (image.reviewStatus() != document.reviewStatus()) {
                throw new IllegalArgumentException("Image and document review statuses differ: " + image.sourcePath());
            }
        }
        if (byDocument.size() != documents.size()) {
            throw new IllegalArgumentException("Every Stage 5 document must have exactly one source image");
        }
        return Map.copyOf(byDocument);
    }

    private static void validateCompleteSourceCoverage(Path sourceRoot, Collection<ImageEntry> images) throws IOException {
        Set<String> manifestPaths = new HashSet<>();
        for (ImageEntry image : images) {
            manifestPaths.add(SourceNormalizer.normalizedSourcePath(image.sourcePath()));
        }
        Set<String> actualImagePaths = new HashSet<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> IMAGE_EXTENSIONS.contains(extension(path)))
                    .map(path -> SourceNormalizer.relativeSourcePath(sourceRoot, path))
                    .map(SourceNormalizer::normalizedSourcePath)
                    .forEach(actualImagePaths::add);
        }
        if (!manifestPaths.equals(actualImagePaths)) {
            Set<String> missing = new HashSet<>(actualImagePaths);
            missing.removeAll(manifestPaths);
            Set<String> stale = new HashSet<>(manifestPaths);
            stale.removeAll(actualImagePaths);
            throw new IllegalArgumentException("Manifest source coverage differs; missing=" + missing + ", stale=" + stale);
        }
    }

    private static void validateDuplicates(
            Collection<ImageEntry> images,
            Map<String, ImageEntry> imagesByDocument,
            Map<String, DocumentEntry> documents
    ) {
        Map<String, List<ImageEntry>> byHash = new HashMap<>();
        for (ImageEntry image : images) {
            byHash.computeIfAbsent(image.sha256(), ignored -> new ArrayList<>()).add(image);
        }
        for (List<ImageEntry> group : byHash.values()) {
            group.sort(Comparator.comparing(ImageEntry::sourcePath, SOURCE_PATH_ORDER));
            ImageEntry representative = group.getFirst();
            DocumentEntry representativeDocument = documents.get(representative.documentId());
            if (!representative.duplicateOfImageId().isEmpty()
                    || !representativeDocument.duplicateOfDocumentId().isEmpty()) {
                throw new IllegalArgumentException("Duplicate representative must not reference another record");
            }
            Set<String> entityIds = new HashSet<>();
            Set<DocumentType> documentTypes = new HashSet<>();
            for (ImageEntry image : group) {
                DocumentEntry document = documents.get(image.documentId());
                entityIds.add(document.entityId());
                documentTypes.add(document.documentType());
            }
            boolean conflict = entityIds.size() > 1 || documentTypes.size() > 1;
            for (int index = 0; index < group.size(); index++) {
                ImageEntry image = group.get(index);
                DocumentEntry document = documents.get(image.documentId());
                if (index > 0 && (!image.duplicateOfImageId().equals(representative.id())
                        || !document.duplicateOfDocumentId().equals(representativeDocument.id()))) {
                    throw new IllegalArgumentException("Invalid deterministic duplicate reference: " + image.sourcePath());
                }
                if (conflict && ((!isUnresolved(document.reviewStatus()))
                        || !isUnresolved(image.reviewStatus()) || document.quizEligible())) {
                    throw new IllegalArgumentException("Conflicting duplicate group must require review or exclusion");
                }
            }
        }
        for (DocumentEntry document : documents.values()) {
            if (!document.duplicateOfDocumentId().isEmpty()) {
                DocumentEntry target = requireReference(
                        documents, document.duplicateOfDocumentId(), "duplicate document"
                );
                if (!target.duplicateOfDocumentId().isEmpty()) {
                    throw new IllegalArgumentException("Duplicate document target is not canonical");
                }
                ImageEntry image = imagesByDocument.get(document.id());
                ImageEntry targetImage = imagesByDocument.get(target.id());
                if (!image.sha256().equals(targetImage.sha256())) {
                    throw new IllegalArgumentException("Duplicate documents have different SHA-256 values");
                }
            }
        }
    }

    private static void validateAliases(
            List<AliasEntry> aliases,
            Map<String, ContinentEntry> continents,
            Map<String, EntityEntry> entities
    ) {
        Set<String> keys = new HashSet<>();
        for (AliasEntry alias : aliases) {
            if (!keys.add(alias.kind() + "\u0000" + alias.sourceValue())) {
                throw new IllegalArgumentException("Duplicate manifest alias: " + alias.sourceValue());
            }
            if (!alias.sourceValue().equals(SourceNormalizer.lookupKey(alias.sourceValue()))) {
                throw new IllegalArgumentException("Alias source value is not normalized: " + alias.sourceValue());
            }
            if (alias.kind() == AliasKind.CONTINENT) {
                ContinentEntry target = requireReference(continents, alias.targetId(), "continent alias");
                if (!alias.canonicalValue().equals(target.code())) {
                    throw new IllegalArgumentException("Continent alias canonical value mismatch");
                }
            } else if (alias.kind() == AliasKind.ENTITY) {
                EntityEntry target = requireReference(entities, alias.targetId(), "entity alias");
                if (!alias.canonicalValue().equals(target.canonicalKey())) {
                    throw new IllegalArgumentException("Entity alias canonical value mismatch");
                }
            } else {
                DocumentType.valueOf(alias.canonicalValue());
                if (!alias.targetId().equals("document-type:" + alias.canonicalValue())) {
                    throw new IllegalArgumentException("Document type alias target mismatch");
                }
            }
        }
    }

    private static boolean isUnresolved(ReviewStatus status) {
        return status == ReviewStatus.NEEDS_REVIEW || status == ReviewStatus.EXCLUDED;
    }

    private static Path resolveContained(Path sourceRoot, String sourcePath) {
        Path resolved = sourceRoot;
        String[] components = sourcePath.split("/", -1);
        for (String component : components) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")) {
                throw new IllegalArgumentException("Invalid relative source path: " + sourcePath);
            }
            resolved = resolved.resolve(component);
        }
        Path normalized = resolved.toAbsolutePath().normalize();
        if (!normalized.startsWith(sourceRoot.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Source path escapes dataset root: " + sourcePath);
        }
        return normalized;
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static <T> Map<String, T> uniqueById(
            List<T> values,
            Function<T, String> id,
            String description
    ) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) {
            String identifier = id.apply(value);
            if (identifier.isBlank() || result.putIfAbsent(identifier, value) != null) {
                throw new IllegalArgumentException("Blank or duplicate " + description + " ID: " + identifier);
            }
        }
        return Map.copyOf(result);
    }

    private static <T> T requireReference(Map<String, T> values, String id, String description) {
        T value = values.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Unknown " + description + " reference: " + id);
        }
        return value;
    }

    private static <T> void requireSorted(List<T> values, Comparator<T> comparator, String description) {
        List<T> sorted = values.stream().sorted(comparator).toList();
        if (!values.equals(sorted)) {
            throw new IllegalArgumentException("Manifest table is not deterministically sorted: " + description);
        }
    }
}
