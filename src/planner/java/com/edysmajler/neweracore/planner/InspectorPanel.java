package com.edysmajler.neweracore.planner;

import com.edysmajler.neweracore.plan.LocationType;
import com.edysmajler.neweracore.plan.PlannedLocation;
import com.edysmajler.neweracore.plan.SnapshotTerrain;
import com.edysmajler.neweracore.plan.TerrainReading;
import com.edysmajler.neweracore.plan.WorldSnapshot;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * The side panel: what the ground under the cursor is like, and the marker being edited.
 *
 * <p>The terrain readout is the reason the planner exists rather than a paint program. A
 * designer choosing where a dam goes needs the enclosure and the water distance at that exact
 * spot, and needs them while the cursor is still moving — a tool that makes you place something
 * before it tells you whether the place was any good has the loop backwards.
 */
public final class InspectorPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  private final SnapshotTerrain terrain;
  private final PlanDocument plan;
  private final MapView map;

  private final JTextArea readout = new JTextArea(14, 26);
  private final JComboBox<LocationType> typeBox = new JComboBox<>(LocationType.values());
  private final JTextField nameField = new JTextField();
  private final JSpinner radiusSpinner =
      new JSpinner(new SpinnerNumberModel(100, 1, 4000, 10));
  private final JTextArea notesArea = new JTextArea(4, 20);
  private final JLabel verdict = new JLabel(" ");
  private final JButton apply = new JButton("Apply");
  private final JButton delete = new JButton("Delete");

  /**
   * Builds the panel.
   *
   * @param snapshot the exported terrain, read for the hover panel
   * @param plan the plan being edited
   * @param map the canvas, told to redraw after an edit
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The snapshot is read-only once loaded and the plan is the one "
          + "document every panel edits: sharing both is the design, and copying either "
          + "would put the panels out of step with each other."
  )
  public InspectorPanel(WorldSnapshot snapshot, PlanDocument plan, MapView map) {
    this.terrain = new SnapshotTerrain(snapshot);
    this.plan = plan;
    this.map = map;

    setLayout(new BorderLayout(0, 8));
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    setPreferredSize(new Dimension(320, 0));

    add(buildReadout(), BorderLayout.NORTH);
    add(buildEditor(), BorderLayout.CENTER);

    map.onHover(point -> showTerrainAt(point.x, point.y));
    map.onSelection(this::showSelection);
    showSelection();
  }

  private JPanel buildReadout() {
    readout.setEditable(false);
    readout.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    readout.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Ground under the cursor"));
    panel.add(readout, BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildEditor() {
    JPanel fields = new JPanel(new GridLayout(0, 2, 6, 6));
    fields.add(new JLabel("Type"));
    fields.add(typeBox);
    fields.add(new JLabel("Name"));
    fields.add(nameField);
    fields.add(new JLabel("Radius"));
    fields.add(radiusSpinner);

    JPanel buttons = new JPanel();
    buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
    buttons.add(apply);
    buttons.add(Box.createHorizontalStrut(6));
    buttons.add(delete);
    buttons.add(Box.createHorizontalGlue());

    apply.addActionListener(event -> applyEdits());
    delete.addActionListener(event -> deleteSelected());

    verdict.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Selected location"));
    panel.add(fields);
    panel.add(verdict);
    panel.add(new JLabel("Notes"));
    panel.add(new JScrollPane(notesArea));
    panel.add(Box.createVerticalStrut(6));
    panel.add(buttons);
    panel.add(Box.createVerticalGlue());
    return panel;
  }

  /**
   * Reports the ground at a position, and what the tool in hand would make of it.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   */
  public void showTerrainAt(int blockX, int blockZ) {
    TerrainReading reading = terrain.readingAt(blockX, blockZ);
    StringBuilder text = new StringBuilder(320);

    line(text, "X", String.valueOf(blockX));
    line(text, "Z", String.valueOf(blockZ));
    line(text, "Y", reading.height() == WorldSnapshot.UNKNOWN_HEIGHT
        ? "unsurveyed"
        : String.valueOf(reading.height()));
    line(text, "Terrain", reading.terrain().name().toLowerCase(Locale.ROOT));
    line(text, "Water", reading.waterDepth() > 0 ? reading.waterDepth() + " deep" : "none");
    line(text, "To water", reading.waterDistance() < 0
        ? "far"
        : reading.waterDistance() + " blocks");
    line(text, "Slope", reading.slope() + " blocks");
    line(text, "Relief", String.valueOf(reading.relief()));
    line(text, "Enclosure", decimal(reading.enclosure()));
    line(text, "Valley", decimal(reading.valley()));
    line(text, "Ground", reading.land() ? (reading.rugged() ? "rugged land" : "land") : "water");

    LocationType hand = map.tool();
    if (hand != null) {
      LocationType.Suitability suitability = hand.rate(reading);
      text.append('\n')
          .append(mark(suitability.rating()))
          .append(' ')
          .append(hand.label())
          .append(": ")
          .append(suitability.reason());
    }

    readout.setText(text.toString());
  }

  /** Loads the selected location into the form, or empties it. */
  public void showSelection() {
    PlannedLocation selected = map.selected().orElse(null);
    boolean present = selected != null;

    typeBox.setEnabled(present);
    nameField.setEnabled(present);
    radiusSpinner.setEnabled(present);
    notesArea.setEnabled(present);
    apply.setEnabled(present);
    delete.setEnabled(present);

    if (!present) {
      nameField.setText("");
      notesArea.setText("");
      verdict.setText("Nothing selected");
      verdict.setForeground(Color.GRAY);
      return;
    }

    typeBox.setSelectedItem(selected.type());
    nameField.setText(selected.name());
    radiusSpinner.setValue(selected.radius());
    notesArea.setText(selected.notes());

    TerrainReading reading = terrain.readingAt(selected.blockX(), selected.blockZ());
    LocationType.Suitability suitability = selected.type().rate(reading);
    verdict.setText(mark(suitability.rating()) + " " + suitability.reason());
    verdict.setForeground(colourOf(suitability.rating()));
  }

  private void applyEdits() {
    PlannedLocation selected = map.selected().orElse(null);
    if (selected == null) {
      return;
    }

    LocationType type = (LocationType) typeBox.getSelectedItem();
    PlannedLocation edited = new PlannedLocation(
        selected.id(),
        type == null ? selected.type() : type,
        nameField.getText(),
        selected.blockX(),
        selected.blockZ(),
        (Integer) radiusSpinner.getValue(),
        notesArea.getText()
    );

    plan.replace(edited);
    map.select(edited);
    map.planChanged();
  }

  private void deleteSelected() {
    map.selected().ifPresent(selected -> {
      plan.remove(selected.id());
      map.select(null);
      map.planChanged();
    });
  }

  private static String mark(LocationType.Rating rating) {
    return switch (rating) {
      case GOOD -> "[good]";
      case FAIR -> "[fair]";
      case POOR -> "[poor]";
    };
  }

  private static Color colourOf(LocationType.Rating rating) {
    return switch (rating) {
      case GOOD -> new Color(0x2E7D32);
      case FAIR -> new Color(0x9A6B00);
      case POOR -> new Color(0xB3261E);
    };
  }

  private static void line(StringBuilder text, String label, String value) {
    text.append(String.format(Locale.ROOT, "%-10s %s%n", label, value));
  }

  private static String decimal(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}
