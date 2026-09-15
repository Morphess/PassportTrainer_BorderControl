package ua.passporttrainer.map;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure hover, focus, selection, and catalogue-data state for map regions. */
public final class MapInteractionState {

    private final Map<String, RegionData> regions;
    private String hoveredRegionId;
    private String focusedRegionId;
    private String selectedRegionId;

    public MapInteractionState(Map<String, RegionData> regions) {
        Objects.requireNonNull(regions, "regions");
        this.regions = Map.copyOf(new LinkedHashMap<>(regions));
    }

    public void hover(String regionId) {
        hoveredRegionId = requireKnownOrNull(regionId);
    }

    public void focus(String regionId) {
        focusedRegionId = requireKnownOrNull(regionId);
    }

    public void select(String regionId) {
        String knownId = requireKnownOrNull(regionId);
        if (knownId != null && !regions.get(knownId).linked()) {
            return;
        }
        selectedRegionId = knownId;
    }

    public Optional<String> hoveredRegionId() {
        return Optional.ofNullable(hoveredRegionId);
    }

    public Optional<String> focusedRegionId() {
        return Optional.ofNullable(focusedRegionId);
    }

    public Optional<String> selectedRegionId() {
        return Optional.ofNullable(selectedRegionId);
    }

    public RegionAppearance appearance(String regionId) {
        RegionData data = requireKnown(regionId);
        return new RegionAppearance(
                !data.linked() || data.documentCount() == 0,
                data.needsReview(),
                regionId.equals(hoveredRegionId),
                regionId.equals(focusedRegionId),
                regionId.equals(selectedRegionId)
        );
    }

    public RegionData regionData(String regionId) {
        return requireKnown(regionId);
    }

    private String requireKnownOrNull(String regionId) {
        if (regionId == null) {
            return null;
        }
        requireKnown(regionId);
        return regionId;
    }

    private RegionData requireKnown(String regionId) {
        RegionData data = regions.get(regionId);
        if (data == null) {
            throw new IllegalArgumentException("Unknown map region: " + regionId);
        }
        return data;
    }

    public record RegionData(boolean linked, int documentCount, boolean needsReview) {
        public RegionData {
            if (documentCount < 0) {
                throw new IllegalArgumentException("documentCount must not be negative");
            }
            if (!linked && documentCount != 0) {
                throw new IllegalArgumentException("Unlinked region cannot have catalogue documents");
            }
        }
    }

    public record RegionAppearance(
            boolean noData,
            boolean needsReview,
            boolean hovered,
            boolean focused,
            boolean selected
    ) {
    }
}
