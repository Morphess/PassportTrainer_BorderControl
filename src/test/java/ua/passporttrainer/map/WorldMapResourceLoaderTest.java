package ua.passporttrainer.map;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapResourceLoaderTest {

    @Test
    void loadsPinnedUkraineViewpointAssetAndStrictMetadata() {
        WorldMapDefinition definition = WorldMapResourceLoader.loadDefault();

        assertEquals(249, definition.regions().size());
        assertEquals(6, definition.groups().size());
        assertEquals(4, definition.entityExclusions().size());
        assertEquals("ne_10m_admin_0_countries_ukr", definition.assetMetadata().dataset());
        assertEquals("5.1.1", definition.assetMetadata().version());
        assertEquals("UKRAINE", definition.assetMetadata().viewpoint());
        assertEquals(
                "5d329b83c0f51b0996519f0fd62073aaf296a3f8013bdcdd73ec2efa80a79ee8",
                definition.assetMetadata().svgSha256()
        );
        assertTrue(definition.regions().containsKey("UKR"));
        assertTrue(definition.regions().containsKey("VAT"));
        assertTrue(definition.regions().containsKey("CSI"));
    }

    @Test
    void rejectsMissingRequiredAssetStream() {
        MapDataException exception = assertThrows(MapDataException.class, () ->
                WorldMapResourceLoader.load(
                        null,
                        bytes("unused"),
                        bytes("unused"),
                        bytes("unused"),
                        bytes("unused")
                )
        );

        assertTrue(exception.getMessage().contains("Every world-map resource stream is required"));
    }

    @Test
    void rejectsSvgDoctypeEvenWhenIntegrityMetadataMatches() throws Exception {
        byte[] svg = ("<?xml version=\"1.0\"?>"
                + "<!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\">"
                + "<path id=\"region-AAA\" d=\"M0 0L1 0L1 1Z\"/></svg>")
                .getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(svg));
        String asset = "source.dataset=test\n"
                + "source.version=1\n"
                + "source.tag=v1\n"
                + "source.viewpoint=TEST\n"
                + "source.shp.sha256=" + "0".repeat(64) + "\n"
                + "svg.sha256=" + hash + "\n"
                + "svg.regionCount=1\n"
                + "svg.subpathCount=1\n"
                + "svg.coordinatePairCount=3\n"
                + "svg.sizeBytes=" + svg.length + "\n";

        MapDataException exception = assertThrows(MapDataException.class, () ->
                WorldMapResourceLoader.load(
                        new ByteArrayInputStream(svg),
                        bytes("unused"),
                        bytes("unused"),
                        bytes("unused"),
                        bytes(asset)
                )
        );

        assertTrue(exception.getMessage().contains("Malformed or unsafe world-map.svg"));
    }

    @Test
    void rejectsMetadataThatOmitsGeneratedGeometry() throws Exception {
        byte[] svg = resourceBytes("/map/world-map.svg");
        byte[] groups = resourceBytes("/map/map-groups.tsv");
        String regions = new String(resourceBytes("/map/map-regions.tsv"), StandardCharsets.UTF_8);
        int firstRowEnd = regions.indexOf('\n', regions.indexOf('\n') + 1);
        String missingFirstRegion = regions.substring(0, regions.indexOf('\n') + 1)
                + regions.substring(firstRowEnd + 1);

        MapDataException exception = assertThrows(MapDataException.class, () ->
                WorldMapResourceLoader.load(
                        new ByteArrayInputStream(svg),
                        new ByteArrayInputStream(groups),
                        bytes(missingFirstRegion),
                        new ByteArrayInputStream(resourceBytes("/map/map-entity-exclusions.tsv")),
                        new ByteArrayInputStream(resourceBytes("/map/map-asset.properties"))
                )
        );

        assertTrue(exception.getMessage().contains("SVG/metadata region mismatch"));
        assertTrue(exception.getMessage().contains("ABW"));
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] resourceBytes(String name) throws Exception {
        try (InputStream input = WorldMapResourceLoaderTest.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource " + name);
            }
            return input.readAllBytes();
        }
    }
}
