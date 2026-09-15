package ua.passporttrainer.map;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapInteractionStateTest {

    @Test
    void exposesIndependentNoDataReviewHoverFocusAndSelectionStates() {
        MapInteractionState state = new MapInteractionState(Map.of(
                "UKR", new MapInteractionState.RegionData(true, 3, true),
                "ABW", new MapInteractionState.RegionData(false, 0, false),
                "CAN", new MapInteractionState.RegionData(true, 0, false)
        ));

        state.hover("UKR");
        state.focus("UKR");
        state.select("UKR");

        MapInteractionState.RegionAppearance ukraine = state.appearance("UKR");
        assertFalse(ukraine.noData());
        assertTrue(ukraine.needsReview());
        assertTrue(ukraine.hovered());
        assertTrue(ukraine.focused());
        assertTrue(ukraine.selected());
        assertTrue(state.appearance("ABW").noData());
        assertTrue(state.appearance("CAN").noData());
    }

    @Test
    void unlinkedRegionCannotReplaceLinkedSelection() {
        MapInteractionState state = new MapInteractionState(Map.of(
                "UKR", new MapInteractionState.RegionData(true, 1, false),
                "ABW", new MapInteractionState.RegionData(false, 0, false)
        ));

        state.select("UKR");
        state.select("ABW");

        assertEquals("UKR", state.selectedRegionId().orElseThrow());
    }
}
