package ua.passporttrainer.manifest;

import ua.passporttrainer.manifest.ManifestCatalogue.AliasEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.AliasKind;
import ua.passporttrainer.manifest.ManifestCatalogue.ContinentEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.DocumentEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.EntityEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.EntityType;
import ua.passporttrainer.manifest.ManifestCatalogue.ImageEntry;
import ua.passporttrainer.manifest.ManifestCatalogue.ReviewStatus;
import ua.passporttrainer.manifest.ManifestCatalogue.Summary;
import ua.passporttrainer.manifest.ManifestMetadata.ContinentRule;
import ua.passporttrainer.manifest.ManifestMetadata.EntityCorrection;
import ua.passporttrainer.manifest.ManifestMetadata.SourceOverride;
import ua.passporttrainer.model.DocumentType;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

/** Generates the canonical catalogue without modifying the source dataset. */
public final class PassportManifestGenerator {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Comparator<String> SOURCE_PATH_ORDER = Comparator
            .comparing(SourceNormalizer::normalizedSourcePath)
            .thenComparing(Function.identity());

    public ManifestCatalogue generate(Path sourceRoot, Path metadataDirectory, Path outputDirectory) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(metadataDirectory, "metadataDirectory");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Path source = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Source root is not a directory: " + sourceRoot);
        }
        Path output = outputDirectory.toAbsolutePath().normalize();
        if (output.startsWith(source)) {
            throw new IllegalArgumentException("Manifest output must not be inside the immutable source dataset");
        }

        ManifestMetadata metadata = ManifestMetadata.load(metadataDirectory);
        List<Path> sourceFiles = sourceFiles(source);
        Set<String> normalizedImagePaths = new HashSet<>();
        Set<String> normalizedNonImagePaths = new HashSet<>();
        List<Path> imageFiles = new ArrayList<>();
        for (Path sourceFile : sourceFiles) {
            String sourcePath = SourceNormalizer.relativeSourcePath(source, sourceFile);
            String normalizedPath = SourceNormalizer.normalizedSourcePath(sourcePath);
            if (isImage(sourceFile)) {
                imageFiles.add(sourceFile);
                normalizedImagePaths.add(normalizedPath);
            } else {
                normalizedNonImagePaths.add(normalizedPath);
                if (!metadata.sourceExclusions().containsKey(normalizedPath)) {
                    throw new IllegalArgumentException("Unclassified non-image source file: " + sourcePath);
                }
            }
        }
        validateMetadataPaths(metadata, normalizedImagePaths, normalizedNonImagePaths);

        Map<String, MutableEntity> entities = new LinkedHashMap<>();
        Map<String, String> entityAliases = new LinkedHashMap<>();
        List<MutableRecord> records = new ArrayList<>();
        for (Path imageFile : imageFiles) {
            records.add(scanImage(source, imageFile, metadata, entities, entityAliases));
        }
        applyDuplicateRelationships(records);

        List<ContinentEntry> continents = metadata.continents().stream()
                .map(rule -> new ContinentEntry(
                        rule.id(), rule.code(), rule.displayNameUk(), rule.sourceName(), rule.sortOrder()
                ))
                .sorted(Comparator.comparingInt(ContinentEntry::sortOrder).thenComparing(ContinentEntry::id))
                .toList();
        List<EntityEntry> entityEntries = entities.values().stream()
                .map(MutableEntity::toEntry)
                .sorted(Comparator.comparing(EntityEntry::id))
                .toList();
        List<DocumentEntry> documents = records.stream()
                .map(MutableRecord::toDocument)
                .sorted(Comparator.comparing(DocumentEntry::id))
                .toList();
        List<ImageEntry> images = records.stream()
                .map(MutableRecord::toImage)
                .sorted(Comparator.comparing(ImageEntry::sourcePath, SOURCE_PATH_ORDER))
                .toList();
        List<AliasEntry> aliases = aliases(metadata, entityAliases);
        Summary summary = new Summary(
                ManifestCatalogue.SCHEMA_VERSION,
                source.getFileName().toString(),
                continents.size(), entityEntries.size(), documents.size(), images.size()
        );
        ManifestCatalogue catalogue = new ManifestCatalogue(
                summary, continents, entityEntries, documents, images, aliases
        );
        PassportManifestValidator.validate(source, catalogue);
        catalogue.write(output);
        PassportManifestValidator.validate(source, output);
        return catalogue;
    }

    private static List<Path> sourceFiles(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> files = paths
                    .filter(path -> !path.equals(sourceRoot))
                    .peek(path -> {
                        if (Files.isSymbolicLink(path)) {
                            throw new IllegalArgumentException("Source dataset must not contain symbolic links: " + path);
                        }
                    })
                    .filter(Files::isRegularFile)
                    .toList();
            return files.stream()
                    .sorted(Comparator.comparing(
                            path -> SourceNormalizer.relativeSourcePath(sourceRoot, path), SOURCE_PATH_ORDER
                    ))
                    .toList();
        }
    }

    private static void validateMetadataPaths(
            ManifestMetadata metadata,
            Set<String> imagePaths,
            Set<String> nonImagePaths
    ) {
        for (String path : metadata.sourceOverrides().keySet()) {
            if (!imagePaths.contains(path)) {
                throw new IllegalArgumentException("Source override does not reference an image: " + path);
            }
        }
        for (String path : metadata.sourceExclusions().keySet()) {
            if (!nonImagePaths.contains(path)) {
                throw new IllegalArgumentException("Source exclusion does not reference a non-image file: " + path);
            }
        }
        for (String path : imagePaths) {
            if (metadata.sourceExclusions().containsKey(path)) {
                throw new IllegalArgumentException("Image cannot be a source exclusion: " + path);
            }
        }
    }

    private static MutableRecord scanImage(
            Path sourceRoot,
            Path imageFile,
            ManifestMetadata metadata,
            Map<String, MutableEntity> entities,
            Map<String, String> entityAliases
    ) throws IOException {
        Path relative = sourceRoot.relativize(imageFile);
        if (relative.getNameCount() < 3) {
            throw new IllegalArgumentException("Image does not have continent/entity structure: " + relative);
        }
        String sourcePath = SourceNormalizer.relativeSourcePath(sourceRoot, imageFile);
        String normalizedSourcePath = SourceNormalizer.normalizedSourcePath(sourcePath);
        SourceOverride override = metadata.sourceOverride(normalizedSourcePath);

        String continentSourceKey = SourceNormalizer.lookupKey(relative.getName(0).toString());
        ContinentRule physicalContinent = metadata.continent(continentSourceKey);
        String sourceEntityName = relative.getName(1).toString();
        String sourceEntityKey = SourceNormalizer.lookupKey(sourceEntityName);
        EntityCorrection sourceCorrection = metadata.entityCorrection(sourceEntityKey);
        String canonicalEntityKey = sourceCorrection != null && sourceCorrection.canonicalKey() != null
                ? sourceCorrection.canonicalKey() : sourceEntityKey;
        if (override != null && override.canonicalEntityKey() != null) {
            canonicalEntityKey = override.canonicalEntityKey();
        } else {
            entityAliases.put(sourceEntityKey, canonicalEntityKey);
        }
        EntityCorrection canonicalCorrection = metadata.entityCorrection(canonicalEntityKey);
        EntityCorrection correction = sourceCorrection != null ? sourceCorrection : canonicalCorrection;
        if (override != null && override.canonicalEntityKey() != null && canonicalCorrection != null) {
            correction = canonicalCorrection;
        }

        String primaryContinentId = correction != null && !blank(correction.primaryContinentId())
                ? correction.primaryContinentId() : physicalContinent.id();
        List<String> alternateContinents = correction == null
                ? List.of() : correction.alternateContinentIds();
        String displaySource = canonicalEntityKey.equals(sourceEntityKey)
                ? sourceEntityName : canonicalEntityKey;
        String displayName = correction != null && !blank(correction.displayNameUk())
                ? correction.displayNameUk() : SourceNormalizer.displayName(displaySource);
        EntityType entityType = correction != null && correction.entityType() != null
                ? correction.entityType() : EntityType.COUNTRY;
        ReviewStatus entityStatus = correction != null && correction.reviewStatus() != null
                ? correction.reviewStatus() : ReviewStatus.AUTO_CLASSIFIED;
        String entityId = SourceNormalizer.stableId("entity:", canonicalEntityKey);
        MutableEntity entity = entities.get(entityId);
        if (entity == null) {
            entity = new MutableEntity(
                    entityId, primaryContinentId, alternateContinents, canonicalEntityKey,
                    displayName, entityType, entityStatus
            );
            entities.put(entityId, entity);
        }
        entity.assertCompatible(primaryContinentId, alternateContinents, canonicalEntityKey, displayName, entityType);
        entity.status = ReviewStatus.mostRestrictive(entity.status, entityStatus);
        if (override == null || override.canonicalEntityKey() == null) {
            entity.sourceNames.add(sourceEntityName);
        }
        entity.addNote(correction == null ? "" : correction.notes());

        String sourceToken = documentTypeToken(relative, metadata);
        DocumentType documentType = metadata.documentType(sourceToken);
        if (override != null && override.documentType() != null) {
            documentType = override.documentType();
        }
        ReviewStatus status = override != null && override.reviewStatus() != null
                ? override.reviewStatus() : ReviewStatus.AUTO_CLASSIFIED;
        status = ReviewStatus.mostRestrictive(status, entityStatus);
        if (documentType == DocumentType.UNKNOWN) {
            status = ReviewStatus.mostRestrictive(status, ReviewStatus.NEEDS_REVIEW);
        }
        boolean quizEligible = override != null && Boolean.TRUE.equals(override.quizEligible());
        String normalizedIdPath = SourceNormalizer.normalizedSourcePath(sourcePath);
        String documentId = SourceNormalizer.stableId("document:", normalizedIdPath);
        String imageId = SourceNormalizer.stableId("image:", normalizedIdPath);
        ImageMetadata imageMetadata = readImageMetadata(imageFile);
        MutableRecord record = new MutableRecord(
                documentId, imageId, entityId, documentType, title(documentType), status, quizEligible,
                sourcePath, imageFile.getFileName().toString(), imageMetadata.mimeType(), imageMetadata.format(),
                imageMetadata.width(), imageMetadata.height(), Files.size(imageFile), sha256(imageFile)
        );
        record.addImageNote(override == null ? "" : override.notes());
        return record;
    }

    private static String documentTypeToken(Path relativePath, ManifestMetadata metadata) {
        String filename = relativePath.getFileName().toString();
        int extension = filename.lastIndexOf('.');
        String stem = extension > 0 ? filename.substring(0, extension) : filename;
        String token = SourceNormalizer.lookupKey(stem);
        if (token.codePoints().allMatch(Character::isDigit) && relativePath.getNameCount() > 3) {
            String parentToken = SourceNormalizer.lookupKey(
                    relativePath.getName(relativePath.getNameCount() - 2).toString()
            );
            if (metadata.documentTypes().containsKey(parentToken)) {
                return parentToken;
            }
        }
        return token;
    }

    private static ImageMetadata readImageMetadata(Path imageFile) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(imageFile.toFile())) {
            if (input == null) {
                throw new IllegalArgumentException("Cannot open image: " + imageFile);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Unsupported image format: " + imageFile);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                String canonicalFormat = format.equals("JPG") ? "JPEG" : format;
                String extension = extension(imageFile);
                String expectedFormat = extension.equals("png") ? "PNG" : "JPEG";
                if (!canonicalFormat.equals(expectedFormat)) {
                    throw new IllegalArgumentException(
                            "Image extension does not match content: " + imageFile + " is " + canonicalFormat
                    );
                }
                String mimeType = canonicalFormat.equals("PNG") ? "image/png" : "image/jpeg";
                return new ImageMetadata(
                        mimeType, canonicalFormat, reader.getWidth(0), reader.getHeight(0)
                );
            } finally {
                reader.dispose();
            }
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static void applyDuplicateRelationships(List<MutableRecord> records) {
        Map<String, List<MutableRecord>> byHash = new HashMap<>();
        for (MutableRecord record : records) {
            byHash.computeIfAbsent(record.sha256, ignored -> new ArrayList<>()).add(record);
        }
        for (List<MutableRecord> group : byHash.values()) {
            if (group.size() < 2) {
                continue;
            }
            group.sort(Comparator.comparing(record -> record.sourcePath, SOURCE_PATH_ORDER));
            MutableRecord representative = group.getFirst();
            boolean conflict = group.stream().map(record -> record.entityId).distinct().count() > 1
                    || group.stream().map(record -> record.documentType).distinct().count() > 1;
            if (conflict) {
                for (MutableRecord record : group) {
                    record.status = ReviewStatus.mostRestrictive(record.status, ReviewStatus.NEEDS_REVIEW);
                    record.quizEligible = false;
                    record.addDocumentNote(
                            "Exact-duplicate group has conflicting entity or document-type metadata."
                    );
                }
            }
            for (int index = 1; index < group.size(); index++) {
                MutableRecord duplicate = group.get(index);
                duplicate.duplicateOfDocumentId = representative.documentId;
                duplicate.duplicateOfImageId = representative.imageId;
            }
        }
    }

    private static List<AliasEntry> aliases(
            ManifestMetadata metadata,
            Map<String, String> entityAliases
    ) {
        List<AliasEntry> aliases = new ArrayList<>();
        for (ContinentRule continent : metadata.continents()) {
            aliases.add(new AliasEntry(
                    AliasKind.CONTINENT, continent.sourceKey(), continent.code(), continent.id()
            ));
        }
        for (Map.Entry<String, String> alias : entityAliases.entrySet()) {
            aliases.add(new AliasEntry(
                    AliasKind.ENTITY, alias.getKey(), alias.getValue(),
                    SourceNormalizer.stableId("entity:", alias.getValue())
            ));
        }
        for (Map.Entry<String, DocumentType> alias : metadata.documentTypes().entrySet()) {
            aliases.add(new AliasEntry(
                    AliasKind.DOCUMENT_TYPE, alias.getKey(), alias.getValue().name(),
                    "document-type:" + alias.getValue().name()
            ));
        }
        return aliases.stream()
                .sorted(Comparator.comparing((AliasEntry alias) -> alias.kind().name())
                        .thenComparing(AliasEntry::sourceValue)
                        .thenComparing(AliasEntry::targetId))
                .toList();
    }

    private static String title(DocumentType type) {
        return switch (type) {
            case ORDINARY_PASSPORT -> "Звичайний паспорт";
            case DIPLOMATIC_PASSPORT -> "Дипломатичний паспорт";
            case SERVICE_PASSPORT -> "Службовий паспорт";
            case NATIONAL_ID -> "Національний документ посвідчення особи";
            case UNKNOWN -> "Невизначений тип документа";
        };
    }

    private static boolean isImage(Path path) {
        return IMAGE_EXTENSIONS.contains(extension(path));
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ImageMetadata(String mimeType, String format, int width, int height) {
    }

    private static final class MutableEntity {
        private final String id;
        private final String primaryContinentId;
        private final List<String> alternateContinentIds;
        private final String canonicalKey;
        private final String displayName;
        private final EntityType entityType;
        private final Set<String> sourceNames = new TreeSet<>(
                Comparator.comparing(SourceNormalizer::lookupKey).thenComparing(Function.identity())
        );
        private final Set<String> notes = new TreeSet<>();
        private ReviewStatus status;

        private MutableEntity(
                String id,
                String primaryContinentId,
                List<String> alternateContinentIds,
                String canonicalKey,
                String displayName,
                EntityType entityType,
                ReviewStatus status
        ) {
            this.id = id;
            this.primaryContinentId = primaryContinentId;
            this.alternateContinentIds = List.copyOf(alternateContinentIds);
            this.canonicalKey = canonicalKey;
            this.displayName = displayName;
            this.entityType = entityType;
            this.status = status;
        }

        private void assertCompatible(
                String candidatePrimaryContinentId,
                List<String> candidateAlternateContinentIds,
                String candidateCanonicalKey,
                String candidateDisplayName,
                EntityType candidateEntityType
        ) {
            if (!primaryContinentId.equals(candidatePrimaryContinentId)
                    || !alternateContinentIds.equals(candidateAlternateContinentIds)
                    || !canonicalKey.equals(candidateCanonicalKey)
                    || !displayName.equals(candidateDisplayName)
                    || entityType != candidateEntityType) {
                throw new IllegalArgumentException("Conflicting canonical entity metadata for " + canonicalKey);
            }
        }

        private void addNote(String note) {
            if (!blank(note)) {
                notes.add(note);
            }
        }

        private EntityEntry toEntry() {
            return new EntityEntry(
                    id, primaryContinentId, alternateContinentIds, canonicalKey, displayName,
                    entityType, List.copyOf(sourceNames), status, String.join("; ", notes)
            );
        }
    }

    private static final class MutableRecord {
        private final String documentId;
        private final String imageId;
        private final String entityId;
        private final DocumentType documentType;
        private final String title;
        private final String sourcePath;
        private final String sourceFilename;
        private final String mimeType;
        private final String format;
        private final int width;
        private final int height;
        private final long fileSize;
        private final String sha256;
        private final Set<String> documentNotes = new TreeSet<>();
        private final Set<String> imageNotes = new TreeSet<>();
        private ReviewStatus status;
        private boolean quizEligible;
        private String duplicateOfDocumentId = "";
        private String duplicateOfImageId = "";

        private MutableRecord(
                String documentId,
                String imageId,
                String entityId,
                DocumentType documentType,
                String title,
                ReviewStatus status,
                boolean quizEligible,
                String sourcePath,
                String sourceFilename,
                String mimeType,
                String format,
                int width,
                int height,
                long fileSize,
                String sha256
        ) {
            this.documentId = documentId;
            this.imageId = imageId;
            this.entityId = entityId;
            this.documentType = documentType;
            this.title = title;
            this.status = status;
            this.quizEligible = quizEligible;
            this.sourcePath = sourcePath;
            this.sourceFilename = sourceFilename;
            this.mimeType = mimeType;
            this.format = format;
            this.width = width;
            this.height = height;
            this.fileSize = fileSize;
            this.sha256 = sha256;
        }

        private void addDocumentNote(String note) {
            if (!blank(note)) {
                documentNotes.add(note);
            }
        }

        private void addImageNote(String note) {
            if (!blank(note)) {
                imageNotes.add(note);
            }
        }

        private DocumentEntry toDocument() {
            return new DocumentEntry(
                    documentId, entityId, documentType, title, "", status, quizEligible,
                    duplicateOfDocumentId, String.join("; ", documentNotes)
            );
        }

        private ImageEntry toImage() {
            return new ImageEntry(
                    imageId, documentId, sourcePath, sourceFilename, mimeType, format,
                    width, height, fileSize, sha256, status, duplicateOfImageId,
                    String.join("; ", imageNotes)
            );
        }
    }

}
