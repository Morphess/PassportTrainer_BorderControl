package ua.passporttrainer.map;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Loads the pinned SVG and its explicit mapping metadata from classpath resources. */
public final class WorldMapResourceLoader {

    private static final String SVG_RESOURCE = "/map/world-map.svg";
    private static final String GROUPS_RESOURCE = "/map/map-groups.tsv";
    private static final String REGIONS_RESOURCE = "/map/map-regions.tsv";
    private static final String EXCLUSIONS_RESOURCE = "/map/map-entity-exclusions.tsv";
    private static final String ASSET_RESOURCE = "/map/map-asset.properties";
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

    private WorldMapResourceLoader() {
    }

    public static WorldMapDefinition loadDefault() {
        try (InputStream svg = requiredResource(SVG_RESOURCE);
             InputStream groups = requiredResource(GROUPS_RESOURCE);
             InputStream regions = requiredResource(REGIONS_RESOURCE);
             InputStream exclusions = requiredResource(EXCLUSIONS_RESOURCE);
             InputStream asset = requiredResource(ASSET_RESOURCE)) {
            return load(svg, groups, regions, exclusions, asset);
        } catch (IOException exception) {
            throw new MapDataException("Could not close world-map resources", exception);
        }
    }

    static WorldMapDefinition load(
            InputStream svgInput,
            InputStream groupsInput,
            InputStream regionsInput,
            InputStream exclusionsInput,
            InputStream assetInput
    ) {
        if (svgInput == null || groupsInput == null || regionsInput == null
                || exclusionsInput == null || assetInput == null) {
            throw new MapDataException("Every world-map resource stream is required");
        }
        try {
            MapAssetMetadata asset = readAssetMetadata(assetInput);
            byte[] svgBytes = svgInput.readAllBytes();
            verifySvgIntegrity(svgBytes, asset);
            SvgData svg = readSvg(svgBytes);
            Map<String, MapGroup> groups = readGroups(groupsInput);
            Map<String, RegionMetadata> regionMetadata = readRegionMetadata(regionsInput);
            Map<String, MapEntityExclusion> exclusions = readExclusions(exclusionsInput);
            Map<String, MapRegion> regions = combineRegions(svg, regionMetadata);
            if (regions.size() != asset.regionCount()) {
                throw new MapDataException("SVG region count does not match map-asset.properties");
            }
            return new WorldMapDefinition(svg.bounds(), groups, regions, exclusions, asset);
        } catch (IOException exception) {
            throw new MapDataException("Could not read world-map resources", exception);
        } catch (IllegalArgumentException exception) {
            throw new MapDataException("Malformed world-map metadata: " + exception.getMessage(), exception);
        }
    }

    private static InputStream requiredResource(String name) {
        InputStream stream = WorldMapResourceLoader.class.getResourceAsStream(name);
        if (stream == null) {
            throw new MapDataException("Missing required world-map resource: " + name);
        }
        return stream;
    }

    private static MapAssetMetadata readAssetMetadata(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        Set<String> expectedKeys = Set.of(
                "source.dataset", "source.version", "source.tag", "source.viewpoint",
                "source.shp.sha256", "svg.sha256", "svg.regionCount", "svg.subpathCount",
                "svg.coordinatePairCount", "svg.sizeBytes"
        );
        if (!properties.stringPropertyNames().equals(expectedKeys)) {
            throw new MapDataException("map-asset.properties keys differ from the required schema");
        }
        return new MapAssetMetadata(
                properties.getProperty("source.dataset"),
                properties.getProperty("source.version"),
                properties.getProperty("source.tag"),
                properties.getProperty("source.viewpoint"),
                properties.getProperty("source.shp.sha256"),
                properties.getProperty("svg.sha256"),
                parsePositiveInt(properties, "svg.regionCount"),
                parsePositiveInt(properties, "svg.subpathCount"),
                parsePositiveInt(properties, "svg.coordinatePairCount"),
                parsePositiveLong(properties, "svg.sizeBytes")
        );
    }

    private static int parsePositiveInt(Properties properties, String key) {
        try {
            int value = Integer.parseInt(properties.getProperty(key));
            if (value <= 0) {
                throw new NumberFormatException("not positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new MapDataException(key + " must be a positive integer", exception);
        }
    }

    private static long parsePositiveLong(Properties properties, String key) {
        try {
            long value = Long.parseLong(properties.getProperty(key));
            if (value <= 0) {
                throw new NumberFormatException("not positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new MapDataException(key + " must be a positive integer", exception);
        }
    }
    private static double parseDouble(String value, String name) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException("not finite");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new MapDataException(name + " must be a finite number", exception);
        }
    }


    private static void verifySvgIntegrity(byte[] svgBytes, MapAssetMetadata asset) {
        if (svgBytes.length != asset.sizeBytes()) {
            throw new MapDataException("world-map.svg size does not match map-asset.properties");
        }
        try {
            String actualHash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(svgBytes)
            );
            if (!actualHash.equals(asset.svgSha256())) {
                throw new MapDataException("world-map.svg SHA-256 does not match map-asset.properties");
            }
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }

    private static SvgData readSvg(byte[] svgBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(svgBytes));
            if (document.getDoctype() != null) {
                throw new MapDataException("world-map.svg must not contain a doctype");
            }
            Element root = document.getDocumentElement();
            if (!SVG_NAMESPACE.equals(root.getNamespaceURI()) || !"svg".equals(root.getLocalName())) {
                throw new MapDataException("world-map.svg root must be an SVG element");
            }
            MapBounds bounds = parseViewBox(root.getAttribute("viewBox"));
            Map<String, SvgPathData> paths = new LinkedHashMap<>();
            collectPaths(root, paths);
            if (paths.isEmpty()) {
                throw new MapDataException("world-map.svg does not contain region paths");
            }
            return new SvgData(bounds, paths);
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new MapDataException("Malformed or unsafe world-map.svg", exception);
        }
    }

    private static MapBounds parseViewBox(String value) {
        String[] parts = value.trim().split("[\\s,]+", -1);
        if (parts.length != 4) {
            throw new MapDataException("world-map.svg viewBox must contain four numbers");
        }
        try {
            return new MapBounds(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3])
            );
        } catch (NumberFormatException exception) {
            throw new MapDataException("world-map.svg has a malformed viewBox", exception);
        }
    }

    private static void collectPaths(Element element, Map<String, SvgPathData> paths) {
        String localName = element.getLocalName();
        if (!Set.of("svg", "g", "path").contains(localName)
                || !SVG_NAMESPACE.equals(element.getNamespaceURI())) {
            throw new MapDataException("world-map.svg contains unsupported element: " + element.getTagName());
        }
        if ("path".equals(localName)) {
            String svgId = element.getAttribute("id");
            if (!svgId.matches("region-[A-Z0-9]{3}")) {
                throw new MapDataException("Invalid SVG region id: " + svgId);
            }
            String regionId = svgId.substring("region-".length());
            String pathData = element.getAttribute("d");
            if (pathData.isBlank()) {
                throw new MapDataException("SVG region has blank path data: " + svgId);
            }
            String fillRule = element.getAttribute("fill-rule");
            if (!fillRule.isEmpty() && !"evenodd".equals(fillRule)) {
                throw new MapDataException("Unsupported SVG fill rule for " + svgId);
            }
            if (paths.putIfAbsent(regionId, new SvgPathData(pathData, "evenodd".equals(fillRule))) != null) {
                throw new MapDataException("Duplicate SVG region id: " + svgId);
            }
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                collectPaths(childElement, paths);
            } else if (child.getNodeType() == Node.TEXT_NODE && !child.getTextContent().isBlank()) {
                throw new MapDataException("world-map.svg contains unexpected text content");
            }
        }
    }

    private static Map<String, MapGroup> readGroups(InputStream input) throws IOException {
        List<Map<String, String>> rows = readTable(
                input,
                List.of(
                        "mapGroupId", "labelKey", "canonicalContinentIds", "wrapDateline",
                        "focusMinX", "focusMinY", "focusWidth", "focusHeight"
                ),
                "map-groups.tsv"
        );
        Map<String, MapGroup> groups = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String id = row.get("mapGroupId");
            String wrapText = row.get("wrapDateline");
            if (!Set.of("true", "false").contains(wrapText)) {
                throw new MapDataException("Invalid wrapDateline value for map group " + id);
            }
            MapGroup group = new MapGroup(
                    id,
                    row.get("labelKey"),
                    List.of(row.get("canonicalContinentIds").split("\\|", -1)),
                    Boolean.parseBoolean(wrapText),
                    new MapBounds(
                            parseDouble(row.get("focusMinX"), id + " focusMinX"),
                            parseDouble(row.get("focusMinY"), id + " focusMinY"),
                            parseDouble(row.get("focusWidth"), id + " focusWidth"),
                            parseDouble(row.get("focusHeight"), id + " focusHeight")
                    )
            );
            if (groups.putIfAbsent(id, group) != null) {
                throw new MapDataException("Duplicate map group id: " + id);
            }
        }
        return groups;
    }

    private static Map<String, RegionMetadata> readRegionMetadata(InputStream input) throws IOException {
        List<Map<String, String>> rows = readTable(
                input,
                List.of("mapRegionId", "mapGroupId", "linkStatus", "canonicalEntityId", "labelKey", "reasonCode"),
                "map-regions.tsv"
        );
        Map<String, RegionMetadata> metadata = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String id = row.get("mapRegionId");
            MapLinkStatus status;
            try {
                status = MapLinkStatus.valueOf(row.get("linkStatus"));
            } catch (IllegalArgumentException exception) {
                throw new MapDataException("Invalid linkStatus for map region " + id, exception);
            }
            RegionMetadata region = new RegionMetadata(
                    row.get("mapGroupId"), status, row.get("canonicalEntityId"),
                    row.get("labelKey"), row.get("reasonCode")
            );
            if (metadata.putIfAbsent(id, region) != null) {
                throw new MapDataException("Duplicate map region id in metadata: " + id);
            }
        }
        return metadata;
    }

    private static Map<String, MapEntityExclusion> readExclusions(InputStream input) throws IOException {
        List<Map<String, String>> rows = readTable(
                input,
                List.of("canonicalEntityId", "mapStatus", "reasonCode"),
                "map-entity-exclusions.tsv"
        );
        Map<String, MapEntityExclusion> exclusions = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String entityId = row.get("canonicalEntityId");
            if (!"LIST_ONLY".equals(row.get("mapStatus"))) {
                throw new MapDataException("Unsupported mapStatus for entity " + entityId);
            }
            MapEntityExclusion exclusion = new MapEntityExclusion(entityId, row.get("reasonCode"));
            if (exclusions.putIfAbsent(entityId, exclusion) != null) {
                throw new MapDataException("Duplicate list-only entity id: " + entityId);
            }
        }
        return exclusions;
    }

    private static Map<String, MapRegion> combineRegions(
            SvgData svg,
            Map<String, RegionMetadata> metadata
    ) {
        if (!svg.paths().keySet().equals(metadata.keySet())) {
            Set<String> missingMetadata = new java.util.TreeSet<>(svg.paths().keySet());
            missingMetadata.removeAll(metadata.keySet());
            Set<String> missingGeometry = new java.util.TreeSet<>(metadata.keySet());
            missingGeometry.removeAll(svg.paths().keySet());
            throw new MapDataException("SVG/metadata region mismatch; missing metadata="
                    + missingMetadata + ", missing geometry=" + missingGeometry);
        }
        Map<String, MapRegion> regions = new LinkedHashMap<>();
        for (Map.Entry<String, SvgPathData> entry : svg.paths().entrySet()) {
            String id = entry.getKey();
            SvgPathData path = entry.getValue();
            RegionMetadata data = metadata.get(id);
            regions.put(id, new MapRegion(
                    id, path.pathData(), path.evenOddFill(), data.mapGroupId(), data.linkStatus(),
                    data.canonicalEntityId(), data.labelKey(), data.reasonCode()
            ));
        }
        return regions;
    }

    private static List<Map<String, String>> readTable(
            InputStream input,
            List<String> expectedHeader,
            String resourceName
    ) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || !List.of(headerLine.split("\\t", -1)).equals(expectedHeader)) {
                throw new MapDataException(resourceName + " has an unexpected header");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    throw new MapDataException(resourceName + " contains a blank row at line " + lineNumber);
                }
                String[] values = line.split("\\t", -1);
                if (values.length != expectedHeader.size()) {
                    throw new MapDataException(resourceName + " has " + values.length
                            + " columns at line " + lineNumber);
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < expectedHeader.size(); index++) {
                    row.put(expectedHeader.get(index), values[index]);
                }
                rows.add(row);
            }
            if (rows.isEmpty()) {
                throw new MapDataException(resourceName + " contains no data rows");
            }
            return rows;
        }
    }

    private record SvgPathData(String pathData, boolean evenOddFill) {
    }

    private record SvgData(MapBounds bounds, Map<String, SvgPathData> paths) {
    }

    private record RegionMetadata(
            String mapGroupId,
            MapLinkStatus linkStatus,
            String canonicalEntityId,
            String labelKey,
            String reasonCode
    ) {
    }
}
