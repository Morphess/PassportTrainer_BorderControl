package ua.passporttrainer.map;

import java.util.Objects;

/** Pure zoom, pan, resize, reset, and fit-to-bounds state in SVG coordinates. */
public final class MapViewportState {

    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 8.0;

    private final MapBounds worldBounds;
    private double viewportWidth = 1.0;
    private double viewportHeight = 1.0;
    private double zoom = MIN_ZOOM;
    private double centerX;
    private double centerY;

    public MapViewportState(MapBounds worldBounds) {
        this.worldBounds = Objects.requireNonNull(worldBounds, "worldBounds");
        centerX = worldBounds.minX() + worldBounds.width() / 2.0;
        centerY = worldBounds.minY() + worldBounds.height() / 2.0;
    }

    public void resize(double width, double height) {
        requirePositiveFinite(width, "width");
        requirePositiveFinite(height, "height");
        viewportWidth = width;
        viewportHeight = height;
        clampCenter();
    }

    public void reset() {
        zoom = MIN_ZOOM;
        centerX = worldBounds.minX() + worldBounds.width() / 2.0;
        centerY = worldBounds.minY() + worldBounds.height() / 2.0;
    }

    public void zoomAt(double factor, double viewportX, double viewportY) {
        requirePositiveFinite(factor, "factor");
        requireFinite(viewportX, "viewportX");
        requireFinite(viewportY, "viewportY");
        Point anchor = worldAt(viewportX, viewportY);
        double newZoom = clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
        if (newZoom == zoom) {
            return;
        }
        zoom = newZoom;
        double scale = scale();
        centerX = anchor.x() - (viewportX - viewportWidth / 2.0) / scale;
        centerY = anchor.y() - (viewportY - viewportHeight / 2.0) / scale;
        clampCenter();
    }

    /** Moves rendered content by the supplied viewport-pixel delta. */
    public void panBy(double deltaX, double deltaY) {
        requireFinite(deltaX, "deltaX");
        requireFinite(deltaY, "deltaY");
        double scale = scale();
        centerX -= deltaX / scale;
        centerY -= deltaY / scale;
        clampCenter();
    }

    public void fitBounds(MapBounds target, double paddingRatio) {
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(paddingRatio) || paddingRatio < 0 || paddingRatio >= 0.5) {
            throw new IllegalArgumentException("paddingRatio must be between 0 and 0.5");
        }
        double availableWidth = viewportWidth * (1.0 - 2.0 * paddingRatio);
        double availableHeight = viewportHeight * (1.0 - 2.0 * paddingRatio);
        double targetScale = Math.min(availableWidth / target.width(), availableHeight / target.height());
        zoom = clamp(targetScale / fitScale(), MIN_ZOOM, MAX_ZOOM);
        centerX = target.minX() + target.width() / 2.0;
        centerY = target.minY() + target.height() / 2.0;
        clampCenter();
    }

    public Transform transform() {
        double scale = scale();
        return new Transform(
                scale,
                viewportWidth / 2.0 - centerX * scale,
                viewportHeight / 2.0 - centerY * scale
        );
    }

    public Point worldAt(double viewportX, double viewportY) {
        Transform transform = transform();
        return new Point(
                (viewportX - transform.translateX()) / transform.scale(),
                (viewportY - transform.translateY()) / transform.scale()
        );
    }

    public double zoom() {
        return zoom;
    }

    public double viewportWidth() {
        return viewportWidth;
    }

    public double viewportHeight() {
        return viewportHeight;
    }

    private double fitScale() {
        return Math.min(viewportWidth / worldBounds.width(), viewportHeight / worldBounds.height());
    }

    private double scale() {
        return fitScale() * zoom;
    }

    private void clampCenter() {
        double halfVisibleWidth = viewportWidth / (2.0 * scale());
        double halfVisibleHeight = viewportHeight / (2.0 * scale());
        centerX = clampAxis(centerX, worldBounds.minX(), worldBounds.maxX(), halfVisibleWidth);
        centerY = clampAxis(centerY, worldBounds.minY(), worldBounds.maxY(), halfVisibleHeight);
    }

    private static double clampAxis(double value, double minimum, double maximum, double halfVisible) {
        if (halfVisible * 2.0 >= maximum - minimum) {
            return (minimum + maximum) / 2.0;
        }
        return clamp(value, minimum + halfVisible, maximum - halfVisible);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requirePositiveFinite(double value, String name) {
        requireFinite(value, name);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public record Transform(double scale, double translateX, double translateY) {
    }

    public record Point(double x, double y) {
    }
}
