package ua.passporttrainer.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapViewportStateTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void fitResizeAndResetKeepWorldCentered() {
        MapViewportState state = new MapViewportState(new MapBounds(0, 0, 1000, 500));
        state.resize(800, 600);

        assertEquals(0.8, state.transform().scale(), EPSILON);
        assertEquals(0, state.transform().translateX(), EPSILON);
        assertEquals(100, state.transform().translateY(), EPSILON);

        state.zoomAt(3, 400, 300);
        state.panBy(100, -50);
        state.reset();

        assertEquals(1, state.zoom(), EPSILON);
        assertEquals(500, state.worldAt(400, 300).x(), EPSILON);
        assertEquals(250, state.worldAt(400, 300).y(), EPSILON);
    }

    @Test
    void zoomKeepsPointerAnchoredAndClampsAtBounds() {
        MapViewportState state = new MapViewportState(new MapBounds(0, 0, 1000, 500));
        state.resize(800, 400);
        MapViewportState.Point before = state.worldAt(650, 120);

        state.zoomAt(2, 650, 120);
        MapViewportState.Point after = state.worldAt(650, 120);

        assertEquals(before.x(), after.x(), EPSILON);
        assertEquals(before.y(), after.y(), EPSILON);
        state.zoomAt(100, 400, 200);
        assertEquals(MapViewportState.MAX_ZOOM, state.zoom(), EPSILON);
        state.zoomAt(0.0001, 400, 200);
        assertEquals(MapViewportState.MIN_ZOOM, state.zoom(), EPSILON);
    }

    @Test
    void panCannotRevealSpaceBeyondWorldBounds() {
        MapViewportState state = new MapViewportState(new MapBounds(0, 0, 1000, 500));
        state.resize(800, 400);
        state.zoomAt(4, 400, 200);

        state.panBy(100_000, 100_000);
        MapViewportState.Point topLeft = state.worldAt(0, 0);
        assertTrue(topLeft.x() >= -EPSILON);
        assertTrue(topLeft.y() >= -EPSILON);

        state.panBy(-200_000, -200_000);
        MapViewportState.Point bottomRight = state.worldAt(800, 400);
        assertTrue(bottomRight.x() <= 1000 + EPSILON);
        assertTrue(bottomRight.y() <= 500 + EPSILON);
    }

    @Test
    void fitsTargetBoundsWithPaddingAndMaximumZoom() {
        MapViewportState state = new MapViewportState(new MapBounds(0, 0, 1000, 500));
        state.resize(1000, 500);

        state.fitBounds(new MapBounds(400, 200, 100, 50), 0.1);

        assertEquals(8, state.zoom(), EPSILON);
        assertEquals(450, state.worldAt(500, 250).x(), EPSILON);
        assertEquals(225, state.worldAt(500, 250).y(), EPSILON);
    }
}
