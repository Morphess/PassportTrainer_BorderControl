package ua.passporttrainer.map;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fully loaded, immutable world-map resource definition. */
public record WorldMapDefinition(
        MapBounds bounds,
        Map<String, MapGroup> groups,
        Map<String, MapRegion> regions,
        Map<String, MapEntityExclusion> entityExclusions,
        MapAssetMetadata assetMetadata
) {
    public WorldMapDefinition {
        bounds = Objects.requireNonNull(bounds, "bounds");
        groups = immutableCopy(groups, "groups");
        regions = immutableCopy(regions, "regions");
        entityExclusions = immutableCopy(entityExclusions, "entityExclusions");
        assetMetadata = Objects.requireNonNull(assetMetadata, "assetMetadata");
    }

    public List<MapRegion> orderedRegions() {
        return List.copyOf(regions.values());
    }

    public List<MapGroup> orderedGroups() {
        return List.copyOf(groups.values());
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> source, String name) {
        Objects.requireNonNull(source, name);
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
