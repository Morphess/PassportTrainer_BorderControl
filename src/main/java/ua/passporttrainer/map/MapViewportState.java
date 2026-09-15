package ua.passporttrainer.map;

import java.util.Objects;

/** Pure zoom, pan, resize, reset, and fit-to-bounds state in SVG coordinates. */
public final class MapViewportState {

    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 8.0;

    private final MapBounds baseWorldBounds;
    private MapBounds navigationBounds;
    private MapBounds fittedBounds;
    private double fittedPaddingRatio;
    private double viewportWidth = 1.0;
    private double viewportHeight = 1.0;
    private double zoom = MIN_ZOOM;
    private double centerX;
    private double centerY;

    public MapViewportState(MapBounds worldBounds) {
        baseWorldBounds = Objects.requireNonNull(worldBounds, "worldBounds");
        navigationBounds = baseWorldBounds;
        centerX = baseWorldBounds.minX() + baseWorldBounds.width() / 2.0;
        centerY = baseWorldBounds.minY() + baseWorldBounds.height() / 2.0;
    }

    public void resize(double width, double height) {
        requirePositiveFinite(width, "width");
        requirePositiveFinite(height, "height");
        viewportWidth = width;
        viewportHeight = height;
        if (fittedBounds == null) {
            clampCenter();
        } else {
            applyFit();
        }
    }

    public void reset() {
        navigationBounds = baseWorldBounds;
        fittedBounds = null;
        zoom = MIN_ZOOM;
        centerX = baseWorldBounds.minX() + baseWorldBounds.width() / 2.0;
        centerY = baseWorldBounds.minY() + baseWorldBounds.height() / 2.0;
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
        fittedBounds = null;
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
        fittedBounds = null;
        double scale = scale();
        centerX -= deltaX / scale;
        centerY -= deltaY / scale;
        clampCenter();
    }

    public void fitBounds(MapBounds target, double paddingRatio) {
        navigationBounds = baseWorldBounds;
        rememberFit(target, paddingRatio);
    }

    void fitWrappedBounds(MapBounds target, double paddingRatio) {
        Objects.requireNonNull(target, "target");
        if (target.width() > baseWorldBounds.width()) {
            throw new IllegalArgumentException("Wrapped target must be narrower than the world");
        }
        double targetCenterX = target.minX() + target.width() / 2.0;
        navigationBounds = new MapBounds(
                targetCenterX - baseWorldBounds.width() / 2.0,
                baseWorldBounds.minY(),
                baseWorldBounds.width(),
                baseWorldBounds.height()
        );
        rememberFit(target, paddingRatio);
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


    private void rememberFit(MapBounds target, double paddingRatio) {
        fittedBounds = Objects.requireNonNull(target, "target");
        if (!Double.isFinite(paddingRatio) || paddingRatio < 0 || paddingRatio >= 0.5) {
            throw new IllegalArgumentException("paddingRatio must be between 0 and 0.5");
        }
        fittedPaddingRatio = paddingRatio;
        applyFit();
    }

    private void applyFit() {
        double availableWidth = viewportWidth * (1.0 - 2.0 * fittedPaddingRatio);
        double availableHeight = viewportHeight * (1.0 - 2.0 * fittedPaddingRatio);
        double targetScale = Math.min(
                availableWidth / fittedBounds.width(),
                availableHeight / fittedBounds.height()
        );
        zoom = clamp(targetScale / fitScale(), MIN_ZOOM, MAX_ZOOM);
        centerX = fittedBounds.minX() + fittedBounds.width() / 2.0;
        centerY = fittedBounds.minY() + fittedBounds.height() / 2.0;
        clampCenter();
    }

    private double fitScale() {
        return Math.min(
                viewportWidth / baseWorldBounds.width(),
                viewportHeight / baseWorldBounds.height()
        );
    }

    private double scale() {
        return fitScale() * zoom;
    }

    private void clampCenter() {
        double halfVisibleWidth = viewportWidth / (2.0 * scale());
        double halfVisibleHeight = viewportHeight / (2.0 * scale());
        centerX = clampAxis(
                centerX, navigationBounds.minX(), navigationBounds.maxX(), halfVisibleWidth
        );
        centerY = clampAxis(
                centerY, navigationBounds.minY(), navigationBounds.maxY(), halfVisibleHeight
        );
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
