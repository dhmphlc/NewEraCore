package com.edysmajler.neweracore.planner;

import com.edysmajler.neweracore.plan.LocationType;
import com.edysmajler.neweracore.plan.WorldPlan;
import com.edysmajler.neweracore.plan.WorldPlanFile;
import com.edysmajler.neweracore.plan.WorldSnapshot;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * The NewEra world planner: look at a world before it is built, then decide what goes in it.
 *
 * <p>Opens a snapshot exported by {@code /nec export} and lets a designer place the world's major
 * locations against the real terrain, saving the result as a plan the plugin will read. It draws no
 * blocks and generates nothing — that division is the whole architecture. The planner decides;
 * NewEraCore builds.
 *
 * <p>What it deliberately does not do is invent anything. There is no automatic civilisation layer,
 * no generated road network, no algorithm choosing where a city belongs. The plugin already tried
 * the automatic version of that and it read as clutter, which is why this tool exists: a hand-made
 * world needs a hand, and the machine's job is to show the terrain clearly enough that the hand can
 * choose well.
 */
public final class PlannerApp {

  private static final String TITLE = "NewEra World Planner";

  private final JFrame frame = new JFrame(TITLE);
  private final JLabel status = new JLabel(" ");
  private final WorldSnapshot snapshot;
  private final PlanDocument plan;
  private final MapView map;
  private final InspectorPanel inspector;

  private Path planFile;

  private PlannerApp(WorldSnapshot snapshot) {
    this.snapshot = snapshot;
    this.plan = new PlanDocument(snapshot);
    this.map = new MapView(snapshot, plan);
    this.inspector = new InspectorPanel(snapshot, plan, map);

    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setJMenuBar(buildMenu());
    frame.setLayout(new BorderLayout());
    frame.add(buildToolbar(), BorderLayout.NORTH);
    frame.add(map, BorderLayout.CENTER);
    frame.add(inspector, BorderLayout.EAST);
    frame.add(buildStatusBar(), BorderLayout.SOUTH);
    frame.setPreferredSize(new Dimension(1400, 950));
    frame.pack();
    frame.setLocationRelativeTo(null);

    map.onHover(point -> {
      inspector.showTerrainAt(point.x, point.y);
      status.setText(String.format(
          Locale.ROOT,
          "  X %d   Z %d   seed %d   %s",
          point.x, point.y, snapshot.seed(), plan.isDirty() ? "unsaved changes" : ""
      ));
    });
    map.onSelection(inspector::showSelection);

    describe();
  }

  /**
   * Opens the planner.
   *
   * @param args an optional path to a snapshot file; a file chooser opens without one
   */
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
        // The cross-platform look and feel is a perfectly good fallback; nothing here needs it
      }

      Path file = args.length > 0 ? Path.of(args[0]) : chooseSnapshot();
      if (file == null) {
        return;
      }

      try {
        WorldSnapshot snapshot = WorldSnapshot.read(file);
        PlannerApp app = new PlannerApp(snapshot);
        app.frame.setVisible(true);
        SwingUtilities.invokeLater(app.map::zoomToFit);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(
            null,
            "Could not open " + file + ":\n" + e.getMessage(),
            TITLE,
            JOptionPane.ERROR_MESSAGE
        );
      }
    });
  }

  private static Path chooseSnapshot() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Open a world snapshot exported by /nec export");
    chooser.setFileFilter(new FileNameExtensionFilter("NewEra world snapshot (*.nep)", "nep"));

    return chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
        ? chooser.getSelectedFile().toPath()
        : null;
  }

  private JMenuBar buildMenu() {
    JMenu file = new JMenu("File");
    file.add(item("Open plan…", this::openPlan));
    file.add(item("Save plan", this::savePlan));
    file.add(item("Save plan as…", this::savePlanAs));
    file.addSeparator();
    file.add(item("Exit", () -> frame.dispose()));

    JMenu view = new JMenu("View");
    view.add(item("Zoom to fit", map::zoomToFit));

    JMenuBar bar = new JMenuBar();
    bar.add(file);
    bar.add(view);
    return bar;
  }

  private JMenuItem item(String label, Runnable action) {
    JMenuItem menuItem = new JMenuItem(label);
    menuItem.addActionListener(event -> action.run());
    return menuItem;
  }

  private JPanel buildToolbar() {
    final JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    ButtonGroup group = new ButtonGroup();

    JToggleButton select = new JToggleButton("Select");
    select.setSelected(true);
    select.addActionListener(event -> map.setTool(null));
    group.add(select);
    tools.add(select);

    for (LocationType type : LocationType.values()) {
      JToggleButton button = new JToggleButton(type.label());
      button.addActionListener(event -> map.setTool(type));
      group.add(button);
      tools.add(button);
    }

    JToggleButton connect = new JToggleButton("Connect");
    connect.setToolTipText("Click two locations to link or unlink them with a planned road");
    connect.addActionListener(event -> map.setConnecting(connect.isSelected()));
    group.add(connect);
    tools.add(connect);

    JPanel overlays = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    Set<Overlay> on = EnumSet.copyOf(map.overlays());
    for (Overlay overlay : Overlay.values()) {
      JCheckBox box = new JCheckBox(overlay.label(), on.contains(overlay));
      box.addActionListener(event -> map.setOverlay(overlay, box.isSelected()));
      overlays.add(box);
    }

    JButton fit = new JButton("Zoom to fit");
    fit.addActionListener(event -> map.zoomToFit());
    overlays.add(fit);

    JPanel bar = new JPanel();
    bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
    bar.add(tools);
    bar.add(overlays);
    return bar;
  }

  private JPanel buildStatusBar() {
    status.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    JPanel bar = new JPanel(new BorderLayout());
    bar.add(status, BorderLayout.WEST);
    return bar;
  }

  private void describe() {
    frame.setTitle(String.format(
        Locale.ROOT,
        "%s — %s, seed %d, %d×%d blocks",
        TITLE, snapshot.worldName(), snapshot.seed(), snapshot.size(), snapshot.size()
    ));
  }

  private void openPlan() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("World plan (*.json)", "json"));
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
      return;
    }

    Path file = chooser.getSelectedFile().toPath();
    try {
      WorldPlan opened = WorldPlanFile.read(file);

      // Loudly, not quietly: a plan over another seed puts every marker on ground nobody looked
      // at, and the map would look perfectly fine while doing it.
      if (!opened.matches(snapshot)) {
        int choice = JOptionPane.showConfirmDialog(
            frame,
            "That plan was designed against seed " + opened.seed()
                + ", but this snapshot is seed " + snapshot.seed()
                + ".\nEvery position in it was chosen by looking at different terrain."
                + "\n\nOpen it anyway?",
            TITLE,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
          return;
        }
      }

      plan.load(opened);
      planFile = file;
      map.select(null);
      map.planChanged();
      status.setText("  Opened " + file.getFileName());
    } catch (IOException e) {
      JOptionPane.showMessageDialog(frame, "Could not read " + file + ":\n" + e.getMessage(),
          TITLE, JOptionPane.ERROR_MESSAGE);
    }
  }

  private void savePlan() {
    if (planFile == null) {
      savePlanAs();
      return;
    }

    try {
      WorldPlanFile.write(plan.toPlan(), planFile);
      plan.markSaved();
      status.setText("  Saved " + planFile.getFileName());
    } catch (IOException e) {
      JOptionPane.showMessageDialog(frame, "Could not write " + planFile + ":\n" + e.getMessage(),
          TITLE, JOptionPane.ERROR_MESSAGE);
    }
  }

  @SuppressFBWarnings(
      value = "PATH_TRAVERSAL_IN",
      justification = "The name only pre-fills a save dialog: the designer picks the actual path, "
          + "and the suggestion comes from the snapshot's own world name."
  )
  private void savePlanAs() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("World plan (*.json)", "json"));
    chooser.setSelectedFile(new File(snapshot.worldName() + "-plan.json"));
    if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
      return;
    }

    planFile = chooser.getSelectedFile().toPath();
    savePlan();
  }
}
