package ua.passporttrainer.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleRole;
import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Affine;
import javafx.scene.layout.Pane;

/** Native JavaFX vector-map surface with bounded zoom, pan, focus, and activation. */
public final class WorldMapPane extends Pane {

    private static final PseudoClass LINKED = PseudoClass.getPseudoClass("linked");
    private static final PseudoClass NO_DATA = PseudoClass.getPseudoClass("no-data");
    private static final PseudoClass NEEDS_REVIEW = PseudoClass.getPseudoClass("needs-review");
    private static final PseudoClass HOVERED = PseudoClass.getPseudoClass("hovered");
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass OUTSIDE_GROUP = PseudoClass.getPseudoClass("outside-group");
    private static final double DRAG_THRESHOLD = 3.0;

    private final WorldMapDefinition definition;
    private final MapInteractionState interactionState;
    private final MapViewportState viewportState;
    private final Group mapGroup = new Group();
    private final Rectangle viewportClip = new Rectangle();
    private final Affine viewportTransform = new Affine();
    private final Map<String, List<RegionView>> regionViews = new LinkedHashMap<>();
    private final List<RegionView> wrappedRegionViews = new ArrayList<>();
    private Consumer<MapRegion> regionActivated = ignored -> { };
    private Function<MapRegion, String> regionLabelProvider = MapRegion::id;
    private String activeGroupId;
    private double dragX;
    private double dragY;
    private boolean dragging;
    private boolean suppressActivation;

    public WorldMapPane(
            WorldMapDefinition definition,
            MapInteractionState interactionState
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.interactionState = Objects.requireNonNull(interactionState, "interactionState");
        viewportState = new MapViewportState(definition.bounds());
        getStyleClass().add("world-map-pane");
        setAccessibleRole(AccessibleRole.PARENT);
        setFocusTraversable(true);
        mapGroup.getTransforms().add(viewportTransform);
        buildRegions();
        getChildren().add(mapGroup);
        setClip(viewportClip);
        installViewportInteraction();
    }

    public void setOnRegionActivated(Consumer<MapRegion> handler) {
        regionActivated = Objects.requireNonNull(handler, "handler");
    }

    public void setRegionLabelProvider(Function<MapRegion, String> provider) {
        regionLabelProvider = Objects.requireNonNull(provider, "provider");
        for (List<RegionView> views : regionViews.values()) {
            for (RegionView view : views) {
                String label = regionLabelProvider.apply(view.region());
                view.path().setAccessibleText(label);
                view.tooltip().setText(label);
            }
        }
    }

    public void zoomIn() {
        viewportState.zoomAt(1.35, getWidth() / 2.0, getHeight() / 2.0);
        applyTransform();
    }

    public void zoomOut() {
        viewportState.zoomAt(1.0 / 1.35, getWidth() / 2.0, getHeight() / 2.0);
        applyTransform();
    }

    public void resetView() {
        activeGroupId = null;
        clearWrappedRegions();
        viewportState.reset();
        for (MapRegion region : definition.regions().values()) {
            refreshAppearance(region.id());
        }
        applyTransform();
    }

    public void focusGroup(String groupId) {
        MapGroup selectedGroup = definition.groups().get(groupId);
        if (selectedGroup == null) {
            throw new IllegalArgumentException("Unknown map group: " + groupId);
        }
        activeGroupId = groupId;
        clearWrappedRegions();
        if (selectedGroup.wrapsDateline()) {
            double wrapOffset = wrapOffset(selectedGroup);
            addWrappedRegions(wrapOffset);
            viewportState.fitWrappedBounds(selectedGroup.focusBounds(), 0.08);
        } else {
            viewportState.fitBounds(selectedGroup.focusBounds(), 0.08);
        }
        for (MapRegion region : definition.regions().values()) {
            refreshAppearance(region.id());
        }
        applyTransform();
    }
    public void selectRegion(String regionId) {
        if (regionId != null && !definition.regions().containsKey(regionId)) {
            throw new IllegalArgumentException("Unknown map region: " + regionId);
        }
        String previous = interactionState.selectedRegionId().orElse(null);
        interactionState.select(regionId);
        refreshAppearance(previous);
        refreshAppearance(regionId);
    }


    @Override
    protected void layoutChildren() {
        double width = Math.max(1.0, getWidth());
        double height = Math.max(1.0, getHeight());
        viewportClip.setWidth(width);
        viewportClip.setHeight(height);
        viewportState.resize(width, height);
        applyTransform();
    }

    @Override
    protected double computePrefWidth(double height) {
        return 900;
    }

    @Override
    protected double computePrefHeight(double width) {
        return 480;
    }

    private void buildRegions() {
        for (MapRegion region : definition.orderedRegions()) {
            addRegionView(region, "region-" + region.id(), 0);
        }
    }

    private void addWrappedRegions(double wrapOffset) {
        for (MapRegion region : definition.orderedRegions()) {
            wrappedRegionViews.add(addRegionView(
                    region,
                    "wrapped-region-" + region.id(),
                    wrapOffset
            ));
        }
    }

    private RegionView addRegionView(MapRegion region, String nodeId, double translateX) {
        SVGPath path = new SVGPath();
        path.setId(nodeId);
        path.setContent(region.pathData());
        path.setFillRule(region.evenOddFill() ? FillRule.EVEN_ODD : FillRule.NON_ZERO);
        path.setTranslateX(translateX);
        path.getStyleClass().addAll(
                "map-region",
                "map-region-" + region.mapGroupId().toLowerCase(Locale.ROOT).replace('_', '-')
        );
        path.setAccessibleRole(region.linked() ? AccessibleRole.BUTTON : AccessibleRole.NODE);
        path.setFocusTraversable(region.linked());
        path.setOnMouseEntered(event -> setHoveredRegion(region.id()));
        path.setOnMouseExited(event -> setHoveredRegion(null));
        path.focusedProperty().addListener((observable, wasFocused, isFocused) ->
                setFocusedRegion(isFocused ? region.id() : null)
        );
        path.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                if (suppressActivation) {
                    suppressActivation = false;
                } else {
                    activate(region);
                }
                event.consume();
            }
        });
        path.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                activate(region);
                event.consume();
            }
        });
        String label = regionLabelProvider.apply(region);
        Tooltip tooltip = new Tooltip(label);
        Tooltip.install(path, tooltip);
        path.setAccessibleText(label);
        RegionView view = new RegionView(region, path, tooltip);
        regionViews.computeIfAbsent(region.id(), ignored -> new ArrayList<>()).add(view);
        mapGroup.getChildren().add(path);
        refreshAppearance(region.id());
        return view;
    }

    private void clearWrappedRegions() {
        for (RegionView view : wrappedRegionViews) {
            Tooltip.uninstall(view.path(), view.tooltip());
            mapGroup.getChildren().remove(view.path());
            regionViews.get(view.region().id()).remove(view);
        }
        wrappedRegionViews.clear();
    }

    private double wrapOffset(MapGroup group) {
        MapBounds world = definition.bounds();
        boolean crossesLeft = group.focusBounds().minX() < world.minX();
        boolean crossesRight = group.focusBounds().maxX() > world.maxX();
        if (crossesLeft == crossesRight) {
            throw new IllegalArgumentException(
                    "Wrapped map group focus must cross exactly one horizontal world edge: " + group.id()
            );
        }
        return crossesRight ? world.width() : -world.width();
    }

    private void installViewportInteraction() {
        setOnScroll(event -> {
            double factor = Math.pow(1.0018, event.getDeltaY());
            viewportState.zoomAt(factor, event.getX(), event.getY());
            applyTransform();
            event.consume();
        });
        setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                dragX = event.getX();
                dragY = event.getY();
                dragging = false;
                suppressActivation = false;
            }
        });
        setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                double deltaX = event.getX() - dragX;
                double deltaY = event.getY() - dragY;
                if (dragging || Math.hypot(deltaX, deltaY) >= DRAG_THRESHOLD) {
                    dragging = true;
                    viewportState.panBy(deltaX, deltaY);
                    applyTransform();
                    dragX = event.getX();
                    dragY = event.getY();
                    event.consume();
                }
            }
        });
        setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY && dragging) {
                suppressActivation = true;
                dragging = false;
                event.consume();
            }
        });
        setOnKeyPressed(event -> {
            double panStep = 40;
            switch (event.getCode()) {
                case LEFT -> viewportState.panBy(panStep, 0);
                case RIGHT -> viewportState.panBy(-panStep, 0);
                case UP -> viewportState.panBy(0, panStep);
                case DOWN -> viewportState.panBy(0, -panStep);
                case PLUS, ADD, EQUALS -> zoomIn();
                case MINUS, SUBTRACT -> zoomOut();
                case DIGIT0, NUMPAD0 -> resetView();
                default -> {
                    return;
                }
            }
            applyTransform();
            event.consume();
        });
    }

    private void activate(MapRegion region) {
        if (!region.linked()) {
            return;
        }
        selectRegion(region.id());
        regionActivated.accept(region);
    }

    private void setHoveredRegion(String regionId) {
        String previous = interactionState.hoveredRegionId().orElse(null);
        interactionState.hover(regionId);
        refreshAppearance(previous);
        refreshAppearance(regionId);
    }

    private void setFocusedRegion(String regionId) {
        String previous = interactionState.focusedRegionId().orElse(null);
        interactionState.focus(regionId);
        refreshAppearance(previous);
        refreshAppearance(regionId);
    }

    private void refreshAppearance(String regionId) {
        if (regionId == null) {
            return;
        }
        MapRegion region = definition.regions().get(regionId);
        MapInteractionState.RegionAppearance appearance = interactionState.appearance(regionId);
        for (RegionView view : regionViews.get(regionId)) {
            SVGPath path = view.path();
            path.pseudoClassStateChanged(LINKED, region.linked());
            path.pseudoClassStateChanged(NO_DATA, appearance.noData());
            path.pseudoClassStateChanged(NEEDS_REVIEW, appearance.needsReview());
            path.pseudoClassStateChanged(HOVERED, appearance.hovered());
            path.pseudoClassStateChanged(FOCUSED, appearance.focused());
            path.pseudoClassStateChanged(SELECTED, appearance.selected());
            path.pseudoClassStateChanged(
                    OUTSIDE_GROUP,
                    activeGroupId != null && !activeGroupId.equals(region.mapGroupId())
            );
        }
    }

    private void applyTransform() {
        MapViewportState.Transform transform = viewportState.transform();
        viewportTransform.setToTransform(
                transform.scale(), 0, transform.translateX(),
                0, transform.scale(), transform.translateY()
        );
    }
    private record RegionView(MapRegion region, SVGPath path, Tooltip tooltip) {
    }

}
