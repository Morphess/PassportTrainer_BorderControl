package ua.passporttrainer.map;

/** Immutable bounds in generated SVG coordinates. */
public record MapBounds(double minX, double minY, double width, double height) {

    public MapBounds {
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Map bounds must be finite and positive");
        }
    }

    public double maxX() {
        return minX + width;
    }

    public double maxY() {
        return minY + height;
    }
}
