package com.edysmajler.neweracore.planner;

import com.edysmajler.neweracore.plan.LocationType;
import com.edysmajler.neweracore.plan.PlannedLocation;
import com.edysmajler.neweracore.plan.PlannedRoad;
import com.edysmajler.neweracore.plan.PlannedSite;
import com.edysmajler.neweracore.plan.WorldSnapshot;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;

/**
 * The map canvas: the world as pixels, with everything planned drawn over it.
 *
 * <p>Two coordinate systems meet here and keeping them apart is most of the work. Blocks are what
 * the designer thinks in and what everything is stored as; screen pixels are what the mouse speaks.
 * Every conversion goes through {@link #screenXof} and {@link #blockXat}, never by hand, because a
 * single hand-rolled conversion that forgets the pan offset puts markers slightly wrong in a way
 * that is invisible until something is built.
 *
 * <p>Sites and markers are drawn as vectors at their real block radius rather than baked into
 * the raster. A town's radius is a distance in the world, so it has to grow when you zoom in;
 * drawn into the image it would stay four pixels wide and read as a dot.
 */
public final class MapView extends JPanel {

  private static final long serialVersionUID = 1L;

  /** Smallest and largest pixels-per-block the view will zoom to. */
  private static final double MIN_SCALE = 0.02;
  private static final double MAX_SCALE = 4.0;

  /** How close to a marker centre a click counts as hitting it, in pixels. */
  private static final int HIT_PIXELS = 10;

  /** Grid spacing in blocks, and the coarser spacing labels are drawn at. */
  private static final int GRID_STEP = 200;
  private static final int GRID_LABEL_STEP = 1000;

  private final WorldSnapshot snapshot;
  private final MapRenderer renderer;
  private final PlanDocument plan;
  private final Set<Overlay> overlays = EnumSet.of(Overlay.WATER, Overlay.SITES, Overlay.GRID);

  private BufferedImage raster;
  private double scale = 0.25;
  private double viewX;
  private double viewZ;
  private Point dragFrom;
  private double dragViewX;
  private double dragViewZ;
  private PlannedLocation selected;
  private PlannedLocation draggingMarker;
  private LocationType tool;
  private boolean connecting;
  private Consumer<Point> hoverListener = point -> {};
  private Runnable selectionListener = () -> {};

  /**
   * Creates a view over a snapshot and the plan being drawn on it.
   *
   * @param snapshot the exported terrain
   * @param plan the plan being edited
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The snapshot is read-only once loaded and the plan is the one "
          + "document every panel edits: sharing both is the design, and copying either "
          + "would put the panels out of step with each other."
  )
  public MapView(WorldSnapshot snapshot, PlanDocument plan) {
    this.snapshot = snapshot;
    this.renderer = new MapRenderer(snapshot);
    this.plan = plan;
    this.raster = renderer.render(overlays);
    this.viewX = snapshot.originX();
    this.viewZ = snapshot.originZ();

    setPreferredSize(new Dimension(900, 900));
    setBackground(new Color(0x14161A));
    setFocusable(true);
    installMouse();
  }

  /**
   * Returns the renderer, so a legend can ask about the height range.
   *
   * @return the renderer
   */
  public MapRenderer renderer() {
    return renderer;
  }

  /**
   * Returns which overlays are on.
   *
   * @return the live set; use {@link #setOverlay} to change it
   */
  public Set<Overlay> overlays() {
    return Set.copyOf(overlays);
  }

  /**
   * Switches an overlay on or off, rebuilding the raster only when it needs it.
   *
   * @param overlay the overlay
   * @param on whether it should be shown
   */
  public void setOverlay(Overlay overlay, boolean on) {
    boolean changed = on ? overlays.add(overlay) : overlays.remove(overlay);
    if (!changed) {
      return;
    }

    if (overlay.isRaster()) {
      raster = renderer.render(overlays);
    }

    repaint();
  }

  /**
   * Sets which kind of place a click on empty ground will create.
   *
   * @param type the type, or null to select rather than place
   */
  public void setTool(LocationType type) {
    this.tool = type;
    this.connecting = false;
    repaint();
  }

  /**
   * Returns the tool in hand.
   *
   * @return the type a click would place, or null when clicks select
   */
  public LocationType tool() {
    return tool;
  }

  /**
   * Puts the view into road mode, where two clicks connect two locations.
   *
   * @param on whether road mode is on
   */
  public void setConnecting(boolean on) {
    this.connecting = on;
    if (on) {
      this.tool = null;
    }
    repaint();
  }

  /**
   * Returns whether clicks currently draw roads.
   *
   * @return true in road mode
   */
  public boolean isConnecting() {
    return connecting;
  }

  /**
   * Returns the selected location.
   *
   * @return the selection, or empty when nothing is selected
   */
  public Optional<PlannedLocation> selected() {
    return Optional.ofNullable(selected);
  }

  /**
   * Selects a location, or clears the selection.
   *
   * @param location the location to select, or null
   */
  public void select(PlannedLocation location) {
    this.selected = location;
    selectionListener.run();
    repaint();
  }

  /**
   * Registers who to tell when the mouse moves, in block coordinates.
   *
   * @param listener called with the block position under the cursor
   */
  public void onHover(Consumer<Point> listener) {
    this.hoverListener = listener;
  }

  /**
   * Registers who to tell when the selection changes.
   *
   * @param listener called after every selection change
   */
  public void onSelection(Runnable listener) {
    this.selectionListener = listener;
  }

  /** Redraws after the plan changed underneath the view. */
  public void planChanged() {
    repaint();
  }

  /** Frames the whole snapshot in the window. */
  public void zoomToFit() {
    int span = Math.max(getWidth(), 1);
    scale = Math.clamp(Math.min(span, Math.max(getHeight(), 1)) / (double) snapshot.size(),
        MIN_SCALE, MAX_SCALE);
    viewX = snapshot.originX();
    viewZ = snapshot.originZ();
    repaint();
  }

  /**
   * Returns the block x under a screen x.
   *
   * @param screenX pixels from the left of the canvas
   * @return the absolute block x
   */
  public int blockXat(int screenX) {
    return (int) Math.floor(viewX + screenX / scale);
  }

  /**
   * Returns the block z under a screen y.
   *
   * @param screenY pixels from the top of the canvas
   * @return the absolute block z
   */
  public int blockZat(int screenY) {
    return (int) Math.floor(viewZ + screenY / scale);
  }

  private double screenXof(double blockX) {
    return (blockX - viewX) * scale;
  }

  private double screenZof(double blockZ) {
    return (blockZ - viewZ) * scale;
  }

  private void installMouse() {
    MouseInputAdapter mouse = new MouseInputAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        requestFocusInWindow();

        if (event.getButton() == MouseEvent.BUTTON2 || event.isShiftDown()) {
          dragFrom = event.getPoint();
          dragViewX = viewX;
          dragViewZ = viewZ;
          return;
        }

        if (event.getButton() == MouseEvent.BUTTON1) {
          clicked(event);
        }
      }

      @Override
      public void mouseDragged(MouseEvent event) {
        if (dragFrom != null) {
          viewX = dragViewX - (event.getX() - dragFrom.x) / scale;
          viewZ = dragViewZ - (event.getY() - dragFrom.y) / scale;
          repaint();
          return;
        }

        if (draggingMarker != null) {
          PlannedLocation moved =
              draggingMarker.movedTo(blockXat(event.getX()), blockZat(event.getY()));
          plan.replace(moved);
          draggingMarker = moved;
          select(moved);
        }
      }

      @Override
      public void mouseReleased(MouseEvent event) {
        dragFrom = null;
        draggingMarker = null;
      }

      @Override
      public void mouseMoved(MouseEvent event) {
        hoverListener.accept(new Point(blockXat(event.getX()), blockZat(event.getY())));
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent event) {
        // Zoom about the cursor rather than the centre: zooming towards what you are looking at is
        // the only behaviour that does not require a pan after every wheel click.
        int anchorX = blockXat(event.getX());
        int anchorZ = blockZat(event.getY());
        double factor = Math.pow(0.9, event.getWheelRotation());
        scale = Math.clamp(scale * factor, MIN_SCALE, MAX_SCALE);
        viewX = anchorX - event.getX() / scale;
        viewZ = anchorZ - event.getY() / scale;
        repaint();
      }
    };

    addMouseListener(mouse);
    addMouseMotionListener(mouse);
    addMouseWheelListener(mouse);
  }

  private void clicked(MouseEvent event) {
    int blockX = blockXat(event.getX());
    int blockZ = blockZat(event.getY());
    Optional<PlannedLocation> hit = plan.nearest(blockX, blockZ, HIT_PIXELS / scale);

    if (connecting) {
      hit.ifPresent(this::connect);
      return;
    }

    if (hit.isPresent()) {
      draggingMarker = hit.get();
      select(hit.get());
      return;
    }

    if (tool != null) {
      select(plan.add(tool, blockX, blockZ));
    } else {
      select(null);
    }
  }

  private void connect(PlannedLocation clicked) {
    if (selected == null || selected.id().equals(clicked.id())) {
      select(clicked);
      return;
    }

    plan.toggleRoad(selected.id(), clicked.id());
    select(clicked);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    drawRaster(g);
    if (overlays.contains(Overlay.GRID)) {
      drawGrid(g);
    }
    drawBoundary(g);
    if (overlays.contains(Overlay.SITES)) {
      drawSites(g);
    }
    drawRoads(g);
    drawLocations(g);

    g.dispose();
  }

  private void drawRaster(Graphics2D g) {
    double pixels = snapshot.resolution() * scale;
    // Nearest neighbour below one screen pixel per sample would shimmer while panning; above it,
    // interpolation would invent terrain that is not in the snapshot. The threshold picks whichever
    // lie is less misleading at the current zoom.
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        pixels >= 1.5
            ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            : RenderingHints.VALUE_INTERPOLATION_BILINEAR
    );

    int x = (int) Math.floor(screenXof(snapshot.originX()));
    int z = (int) Math.floor(screenZof(snapshot.originZ()));
    int width = (int) Math.ceil(snapshot.size() * scale);
    g.drawImage(raster, x, z, Math.max(width, 1), Math.max(width, 1), null);
  }

  private void drawGrid(Graphics2D g) {
    g.setStroke(new BasicStroke(1f));
    int from = snapshot.originX();
    int to = snapshot.originX() + snapshot.size();
    int fromZ = snapshot.originZ();
    int toZ = snapshot.originZ() + snapshot.size();

    for (int line = ceilTo(from, GRID_STEP); line <= to; line += GRID_STEP) {
      boolean labelled = line % GRID_LABEL_STEP == 0;
      g.setColor(new Color(255, 255, 255, labelled ? 70 : 28));
      int screen = (int) Math.round(screenXof(line));
      g.drawLine(screen, (int) Math.round(screenZof(fromZ)), screen,
          (int) Math.round(screenZof(toZ)));

      if (labelled) {
        g.setColor(new Color(255, 255, 255, 140));
        g.drawString(String.valueOf(line), screen + 3, 14);
      }
    }

    for (int line = ceilTo(fromZ, GRID_STEP); line <= toZ; line += GRID_STEP) {
      boolean labelled = line % GRID_LABEL_STEP == 0;
      g.setColor(new Color(255, 255, 255, labelled ? 70 : 28));
      int screen = (int) Math.round(screenZof(line));
      g.drawLine((int) Math.round(screenXof(from)), screen,
          (int) Math.round(screenXof(to)), screen);

      if (labelled) {
        g.setColor(new Color(255, 255, 255, 140));
        g.drawString(String.valueOf(line), 4, screen - 3);
      }
    }
  }

  private void drawBoundary(Graphics2D g) {
    g.setColor(new Color(0xF2, 0xE9, 0xD8, 200));
    g.setStroke(new BasicStroke(2f));
    int x = (int) Math.round(screenXof(snapshot.originX()));
    int z = (int) Math.round(screenZof(snapshot.originZ()));
    int side = (int) Math.round(snapshot.size() * scale);
    g.drawRect(x, z, side, side);
  }

  private void drawSites(Graphics2D g) {
    g.setStroke(new BasicStroke(1.5f));

    for (PlannedSite site : snapshot.sites()) {
      Color colour = switch (site.kind()) {
        case STRUCTURE -> new Color(0x6C, 0xC5, 0xE8);
        case TOWN -> new Color(0xE8, 0xC8, 0x6C);
        case CRATER -> new Color(0xE8, 0x6C, 0x6C);
      };

      double radius = Math.max(site.radius() * scale, 3.0);
      double centreX = screenXof(site.centerX());
      double centreZ = screenZof(site.centerZ());

      g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 40));
      g.fillOval((int) (centreX - radius), (int) (centreZ - radius),
          (int) (radius * 2), (int) (radius * 2));
      g.setColor(colour);
      g.drawOval((int) (centreX - radius), (int) (centreZ - radius),
          (int) (radius * 2), (int) (radius * 2));

      if (scale > 0.12) {
        g.drawString(site.id(), (int) centreX + 4, (int) centreZ - 4);
      }
    }
  }

  private void drawRoads(Graphics2D g) {
    g.setColor(new Color(0xF2, 0xE9, 0xD8, 160));
    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
        new float[] {8f, 6f}, 0f));

    for (PlannedRoad road : plan.roads()) {
      Optional<PlannedLocation> from = plan.byId(road.fromId());
      Optional<PlannedLocation> to = plan.byId(road.toId());
      if (from.isEmpty() || to.isEmpty()) {
        continue;
      }

      g.drawLine(
          (int) Math.round(screenXof(from.get().blockX())),
          (int) Math.round(screenZof(from.get().blockZ())),
          (int) Math.round(screenXof(to.get().blockX())),
          (int) Math.round(screenZof(to.get().blockZ()))
      );
    }
  }

  private void drawLocations(Graphics2D g) {
    Font font = getFont().deriveFont(Font.BOLD, 11f);
    g.setFont(font);

    for (PlannedLocation location : plan.locations()) {
      boolean isSelected = selected != null && selected.id().equals(location.id());
      int centreX = (int) Math.round(screenXof(location.blockX()));
      int centreZ = (int) Math.round(screenZof(location.blockZ()));
      int radius = (int) Math.round(location.radius() * scale);

      g.setStroke(new BasicStroke(isSelected ? 2.5f : 1.5f));
      g.setColor(new Color(0xFF, 0xFF, 0xFF, isSelected ? 200 : 110));
      g.drawOval(centreX - radius, centreZ - radius, radius * 2, radius * 2);

      g.setColor(isSelected ? new Color(0xFF, 0xD9, 0x66) : new Color(0xF2, 0xE9, 0xD8));
      g.fillRect(centreX - 4, centreZ - 4, 8, 8);
      g.setColor(new Color(0x14, 0x16, 0x1A));
      g.drawRect(centreX - 4, centreZ - 4, 8, 8);

      g.setColor(new Color(0xF2, 0xE9, 0xD8));
      g.drawString(label(location), centreX + 8, centreZ + 4);
    }
  }

  private static String label(PlannedLocation location) {
    return location.name().isBlank() ? location.type().label() : location.name();
  }

  private static int ceilTo(int value, int step) {
    return (int) (Math.ceil(value / (double) step) * step);
  }
}
