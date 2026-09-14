package ua.passporttrainer.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.manifest.ManifestCatalogue.AliasKind;
import ua.passporttrainer.manifest.ManifestCatalogue.ReviewStatus;
import ua.passporttrainer.model.DocumentType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassportManifestGeneratorTest {
    @TempDir
    Path tempDirectory;

    @Test
    void resolvesEntityAliasesIntoOneCanonicalEntity() throws IOException {
        Fixture fixture = fixture("aliases");
        writeImage(fixture.source().resolve("Європа/Стара назва/ззп.png"), 0x101010);
        writeImage(fixture.source().resolve("Європа/Нова назва/ззп.png"), 0x202020);
        writeMetadata(fixture, String.join("\n",
                entityRow("стара назва", "канонічна назва", "Канонічна назва", "COUNTRY", "VERIFIED"),
                entityRow("нова назва", "канонічна назва", "Канонічна назва", "COUNTRY", "VERIFIED")
        ), "");

        ManifestCatalogue catalogue = generate(fixture);

        assertEquals(1, catalogue.entities().size());
        assertEquals(List.of("Нова назва", "Стара назва"), catalogue.entities().getFirst().sourceNames());
        assertEquals(2, catalogue.aliases().stream().filter(alias -> alias.kind() == AliasKind.ENTITY).count());
    }

    @Test
    void mapsDocumentTypeAliasesToCanonicalContract() throws IOException {
        Fixture fixture = fixture("types");
        writeImage(fixture.source().resolve("Європа/Країна/ззп.png"), 0x010101);
        writeImage(fixture.source().resolve("Європа/Країна/id.png"), 0x020202);
        writeImage(fixture.source().resolve("Європа/Країна/невідомий.png"), 0x030303);
        writeMetadata(fixture, "", "");

        ManifestCatalogue catalogue = generate(fixture);

        assertEquals(1, catalogue.documents().stream()
                .filter(document -> document.documentType() == DocumentType.ORDINARY_PASSPORT).count());
        assertEquals(1, catalogue.documents().stream()
                .filter(document -> document.documentType() == DocumentType.NATIONAL_ID).count());
        assertEquals(1, catalogue.documents().stream()
                .filter(document -> document.documentType() == DocumentType.UNKNOWN).count());
    }

    @Test
    void generatesStableIdsAcrossInstallationDirectories() throws IOException {
        Fixture first = fixture("first/location");
        Fixture second = fixture("second/location");
        writeImage(first.source().resolve("Європа/Країна/ззп.png"), 0x111111);
        writeImage(second.source().resolve("Європа/Країна/ззп.png"), 0x111111);
        writeMetadata(first, "", "");
        writeMetadata(second, "", "");

        ManifestCatalogue firstCatalogue = generate(first);
        ManifestCatalogue secondCatalogue = generate(second);

        assertEquals(firstCatalogue.entities().getFirst().id(), secondCatalogue.entities().getFirst().id());
        assertEquals(firstCatalogue.documents().getFirst().id(), secondCatalogue.documents().getFirst().id());
        assertEquals(firstCatalogue.images().getFirst().id(), secondCatalogue.images().getFirst().id());
    }

    @Test
    void marksExactDuplicatesWithoutRemovingPhysicalRecords() throws IOException {
        Fixture fixture = fixture("duplicates");
        Path first = fixture.source().resolve("Європа/Країна/ззп.png");
        Path second = fixture.source().resolve("Європа/Країна/ззп (2).png");
        writeImage(first, 0x123456);
        Files.createDirectories(second.getParent());
        Files.copy(first, second);
        writeMetadata(fixture, "", "");

        ManifestCatalogue catalogue = generate(fixture);

        assertEquals(2, catalogue.documents().size());
        assertEquals(2, catalogue.images().size());
        assertEquals(1, catalogue.documents().stream()
                .filter(document -> !document.duplicateOfDocumentId().isEmpty()).count());
        assertEquals(1, catalogue.images().stream()
                .filter(image -> !image.duplicateOfImageId().isEmpty()).count());
        assertTrue(catalogue.images().getFirst().duplicateOfImageId().isEmpty());
        assertEquals(catalogue.images().getFirst().id(), catalogue.images().get(1).duplicateOfImageId());
    }

    @Test
    void rejectsManifestWhenReferencedSourcePathDisappears() throws IOException {
        Fixture fixture = fixture("missing-source");
        Path image = fixture.source().resolve("Європа/Країна/ззп.png");
        writeImage(image, 0x111111);
        writeMetadata(fixture, "", "");
        generate(fixture);
        Files.delete(image);

        assertThrows(
                IllegalArgumentException.class,
                () -> PassportManifestValidator.validate(fixture.source(), fixture.output())
        );
    }

    @Test
    void marksUnknownDocumentTypeAsNeedsReviewAndQuizIneligible() throws IOException {
        Fixture fixture = fixture("unknown");
        writeImage(fixture.source().resolve("Європа/Країна/mystery.png"), 0x111111);
        writeMetadata(fixture, "", "");

        ManifestCatalogue catalogue = generate(fixture);
        var document = catalogue.documents().getFirst();

        assertEquals(DocumentType.UNKNOWN, document.documentType());
        assertEquals(ReviewStatus.NEEDS_REVIEW, document.reviewStatus());
        assertFalse(document.quizEligible());
    }

    @Test
    void propagatesEntityReviewStatusToDocuments() throws IOException {
        Fixture fixture = fixture("review");
        writeImage(fixture.source().resolve("Європа/Країна/ззп.png"), 0x111111);
        writeMetadata(fixture, entityRow("країна", "", "Країна", "COUNTRY", "NEEDS_REVIEW"), "");

        ManifestCatalogue catalogue = generate(fixture);

        assertEquals(ReviewStatus.NEEDS_REVIEW, catalogue.entities().getFirst().reviewStatus());
        assertEquals(ReviewStatus.NEEDS_REVIEW, catalogue.documents().getFirst().reviewStatus());
        assertFalse(catalogue.documents().getFirst().quizEligible());
    }

    @Test
    void allowsQuizEligibilityOnlyForExplicitlyVerifiedRecords() throws IOException {
        Fixture fixture = fixture("eligible");
        writeImage(fixture.source().resolve("Європа/Країна/ззп.png"), 0x111111);
        writeMetadata(
                fixture,
                entityRow("країна", "", "Країна", "COUNTRY", "VERIFIED"),
                "Європа/Країна/ззп.png\t\tORDINARY_PASSPORT\tVERIFIED\ttrue\tExplicit test review."
        );

        ManifestCatalogue catalogue = generate(fixture);

        assertTrue(catalogue.documents().getFirst().quizEligible());
        assertEquals(ReviewStatus.VERIFIED, catalogue.documents().getFirst().reviewStatus());
    }

    @Test
    void rejectsQuizEligibleUnknownRecord() throws IOException {
        Fixture fixture = fixture("invalid-eligible");
        writeImage(fixture.source().resolve("Європа/Країна/mystery.png"), 0x111111);
        writeMetadata(
                fixture,
                entityRow("країна", "", "Країна", "COUNTRY", "VERIFIED"),
                "Європа/Країна/mystery.png\t\tUNKNOWN\tVERIFIED\ttrue\tInvalid by contract."
        );

        assertThrows(IllegalArgumentException.class, () -> generate(fixture));
    }

    @Test
    void rejectsInvalidReviewStatusMetadata() throws IOException {
        Fixture fixture = fixture("invalid-status");
        writeImage(fixture.source().resolve("Європа/Країна/ззп.png"), 0x111111);
        writeMetadata(
                fixture,
                "країна\t\tКраїна\tCOUNTRY\tcontinent:europe\t\tBROKEN\tInvalid status.",
                ""
        );

        assertThrows(IllegalArgumentException.class, () -> generate(fixture));
    }

    @Test
    void rejectsDuplicateReferenceToMissingRecord() throws IOException {
        Fixture fixture = fixture("invalid-duplicate");
        Path first = fixture.source().resolve("Європа/Країна/ззп.png");
        Path second = fixture.source().resolve("Європа/Країна/ззп (2).png");
        writeImage(first, 0x123456);
        Files.createDirectories(second.getParent());
        Files.copy(first, second);
        writeMetadata(fixture, "", "");
        generate(fixture);
        Path images = fixture.output().resolve("images.tsv");
        List<String> lines = Files.readAllLines(images, StandardCharsets.UTF_8);
        for (int index = 1; index < lines.size(); index++) {
            String[] values = lines.get(index).split("\\t", -1);
            if (!values[11].isEmpty()) {
                values[11] = "image:missing";
                lines.set(index, String.join("\t", values));
                break;
            }
        }
        Files.writeString(images, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);

        assertThrows(
                IllegalArgumentException.class,
                () -> PassportManifestValidator.validate(fixture.source(), fixture.output())
        );
    }

    @Test
    void generatesByteIdenticalOutputIndependentOfFileCreationOrder() throws IOException {
        Fixture first = fixture("order-a/location");
        Fixture second = fixture("order-b/location");
        writeImage(first.source().resolve("Європа/Бета/id.png"), 0x222222);
        writeImage(first.source().resolve("Європа/Альфа/ззп.png"), 0x111111);
        writeImage(second.source().resolve("Європа/Альфа/ззп.png"), 0x111111);
        writeImage(second.source().resolve("Європа/Бета/id.png"), 0x222222);
        writeMetadata(first, "", "");
        writeMetadata(second, "", "");

        generate(first);
        generate(second);

        List<Path> firstFiles;
        try (var paths = Files.list(first.output())) {
            firstFiles = paths.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        List<Path> secondFiles;
        try (var paths = Files.list(second.output())) {
            secondFiles = paths.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        assertEquals(
                firstFiles.stream().map(path -> path.getFileName().toString()).toList(),
                secondFiles.stream().map(path -> path.getFileName().toString()).toList()
        );
        for (int index = 0; index < firstFiles.size(); index++) {
            assertArrayEquals(Files.readAllBytes(firstFiles.get(index)), Files.readAllBytes(secondFiles.get(index)));
        }
    }

    private ManifestCatalogue generate(Fixture fixture) throws IOException {
        return new PassportManifestGenerator().generate(
                fixture.source(), fixture.metadata(), fixture.output()
        );
    }

    private Fixture fixture(String relativeBase) throws IOException {
        Path base = tempDirectory.resolve(relativeBase);
        Path source = base.resolve("source");
        Path metadata = base.resolve("metadata");
        Path output = base.resolve("manifest");
        Files.createDirectories(source);
        Files.createDirectories(metadata);
        return new Fixture(source, metadata, output);
    }

    private static void writeMetadata(Fixture fixture, String entityRows, String overrideRows) throws IOException {
        writeTsv(fixture.metadata().resolve("continents.tsv"), String.join("\n",
                "sourceKey\tid\tcode\tdisplayNameUk\tsourceName\tsortOrder",
                "європа\tcontinent:europe\tEUROPE\tЄвропа\tЄвропа\t1"
        ));
        writeTsv(fixture.metadata().resolve("entity-corrections.tsv"), withRows(
                "sourceKey\tcanonicalKey\tdisplayNameUk\tentityType\tprimaryContinentId\talternateContinentIds\treviewStatus\tnotes",
                entityRows
        ));
        writeTsv(fixture.metadata().resolve("document-type-aliases.tsv"), String.join("\n",
                "sourceToken\tdocumentType",
                "ззп\tORDINARY_PASSPORT",
                "ззп (2)\tORDINARY_PASSPORT",
                "id\tNATIONAL_ID"
        ));
        writeTsv(fixture.metadata().resolve("source-overrides.tsv"), withRows(
                "sourcePath\tcanonicalEntityKey\tdocumentType\treviewStatus\tquizEligible\tnotes",
                overrideRows
        ));
        writeTsv(fixture.metadata().resolve("source-exclusions.tsv"),
                "sourcePath\treviewStatus\tnotes");
    }

    private static String entityRow(
            String sourceKey,
            String canonicalKey,
            String displayName,
            String entityType,
            String reviewStatus
    ) {
        return String.join("\t", sourceKey, canonicalKey, displayName, entityType,
                "continent:europe", "", reviewStatus, "Test metadata.");
    }

    private static String withRows(String header, String rows) {
        return rows.isEmpty() ? header : header + "\n" + rows;
    }

    private static void writeTsv(Path path, String content) throws IOException {
        Files.writeString(path, content + "\n", StandardCharsets.UTF_8);
    }

    private static void writeImage(Path path, int rgb) throws IOException {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[6];
        Arrays.fill(pixels, rgb);
        image.setRGB(0, 0, 3, 2, pixels, 0, 3);
        if (!ImageIO.write(image, "PNG", path.toFile())) {
            throw new IOException("PNG writer unavailable");
        }
    }

    private record Fixture(Path source, Path metadata, Path output) {
    }
}
