package colorpicker.ui.tools;

import colorpicker.App;
import colorpicker.core.ColorChanger;
import colorpicker.core.HSVColor;
import colorpicker.core.NumericInput;
import colorpicker.core.VB;
import colorpicker.i18n.Lang;
import colorpicker.ui.Resources;
import colorpicker.ui.UiUtil;
import colorpicker.ui.components.ColorSwatch;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ItemEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * "Advanced random color" tool (port of {@code AdvancedRandomColorForm}).
 *
 * <p>Generates a random colour in RGB or HSV, either with the "high channel"
 * shortcuts of the Simple tab or with explicit from/to ranges in the Advanced
 * tab, optionally grayscale and/or web-safe. The result can be dragged as
 * {@code #RRGGBB} text or sent to the main window with "Select and close".
 */
public final class AdvancedRandomColorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final int SIMPLE_TAB = 0;
    private static final int ADVANCED_TAB = 1;
    /** {@code MaxLength} of the range text boxes. */
    private static final int FIELD_MAX_LENGTH = 3;
    private static final int ROWS = 3;
    /** Side of the output colour box at 100 %; fixed lengths below go through UIScale.scale. */
    private static final int SWATCH_SIZE = 56;

    private static AdvancedRandomColorDialog instance;

    private final transient Random random = new Random();
    private final transient Runnable langListener = this::updateLang;

    private final JRadioButton rgbRadio = new JRadioButton();
    private final JRadioButton hsvRadio = new JRadioButton();
    private final JCheckBox grayChk = new JCheckBox();
    private final JCheckBox websafeChk = new JCheckBox();

    private final JTabbedPane tabControl = new JTabbedPane();
    private final JPanel rgbPnl = new JPanel();
    private final JPanel hsvPnl = new JPanel();
    private final JCheckBox redChk = new JCheckBox();
    private final JCheckBox greenChk = new JCheckBox();
    private final JCheckBox blueChk = new JCheckBox();
    private final JCheckBox satChk = new JCheckBox();
    private final JCheckBox valueChk = new JCheckBox();

    private final JLabel[] rowLabels = new JLabel[ROWS];
    private final JLabel[] arrows = new JLabel[ROWS];
    private final JTextField[] fromFields = new JTextField[ROWS];
    private final JTextField[] toFields = new JTextField[ROWS];

    private final JLabel outputLabel = new JLabel("", SwingConstants.CENTER);
    private final ColorSwatch colorPanel =
            new ColorSwatch(null, UIScale.scale(SWATCH_SIZE), UIScale.scale(SWATCH_SIZE));
    private final JButton generateButton = new JButton();
    private final JButton selectButton = new JButton();
    private final JButton cancelBtn = new JButton();

    /** Selected tab: 0 simple, 1 advanced ({@code mode} in the original). */
    private int mode = SIMPLE_TAB;

    private AdvancedRandomColorDialog(Window owner) {
        super(owner, ModalityType.MODELESS);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setIconImages(Resources.appIcons());
        setResizable(false);

        buildUi();
        wireEvents();
        installTabOrder();

        updateLang();
        onOptionsChanged();
        pack();
        Lang.addListener(langListener);
    }

    /** Shows the tool window (reusing the open one), like VB's Form.Show() + BringToFront(). */
    public static void showDialog(Window owner) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new AdvancedRandomColorDialog(owner);
            instance.setLocationRelativeTo(owner);
        }
        instance.setVisible(true);
        instance.toFront();
    }

    @Override
    public void dispose() {
        Lang.removeListener(langListener);
        if (instance == this) {
            instance = null;
        }
        super.dispose();
    }

    // ---------------------------------------------------------------- layout

    private void buildUi() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(UIScale.scale(new Insets(8, 8, 8, 8))));

        tabControl.addTab("", buildSimpleTab());
        tabControl.addTab("", buildAdvancedTab());
        // FlatLaf: framed like a WinForms TabControl (ignored by other look and feels)
        tabControl.putClientProperty("JTabbedPane.hasFullBorder", Boolean.TRUE);

        ButtonGroup spaceGroup = new ButtonGroup();
        spaceGroup.add(rgbRadio);
        spaceGroup.add(hsvRadio);
        rgbRadio.setSelected(true);

        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[] {rgbRadio, hsvRadio, grayChk, websafeChk}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            options.add(c);
        }

        JPanel output = new JPanel(new GridBagLayout());
        GridBagConstraints oc = new GridBagConstraints();
        oc.gridx = 0;
        oc.gridy = 0;
        oc.fill = GridBagConstraints.HORIZONTAL;
        oc.insets = UIScale.scale(new Insets(0, 0, 2, 0));
        output.add(outputLabel, oc);
        oc.gridy = 1;
        oc.fill = GridBagConstraints.NONE;
        oc.insets = new Insets(0, 0, 0, 0);
        output.add(colorPanel, oc);

        generateButton.setFont(generateButton.getFont().deriveFont(Font.BOLD));
        selectButton.setEnabled(false);
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue());
        buttons.add(cancelBtn);
        buttons.add(Box.createHorizontalStrut(UIScale.scale(6)));
        buttons.add(selectButton);
        buttons.add(Box.createHorizontalStrut(UIScale.scale(6)));
        buttons.add(generateButton);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.gridx = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.BOTH;
        content.add(tabControl, c);

        c.gridx = 1;
        c.anchor = GridBagConstraints.LAST_LINE_START;
        c.fill = GridBagConstraints.NONE;
        c.insets = UIScale.scale(new Insets(0, 6, 1, 0));
        content.add(options, c);

        c.gridx = 2;
        c.weightx = 1;
        c.anchor = GridBagConstraints.LAST_LINE_END;
        c.insets = UIScale.scale(new Insets(0, 24, 8, 0));
        content.add(output, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = UIScale.scale(new Insets(9, 0, 0, 0));
        content.add(buttons, c);

        setContentPane(content);
    }

    private JPanel buildSimpleTab() {
        fillCheckPanel(rgbPnl, redChk, greenChk, blueChk);
        fillCheckPanel(hsvPnl, satChk, valueChk);
        JPanel simple = new JPanel(new GridLayout(1, 2, UIScale.scale(6), 0));
        simple.setBorder(new EmptyBorder(UIScale.scale(new Insets(5, 6, 5, 6))));
        simple.add(rgbPnl);
        simple.add(hsvPnl);
        return simple;
    }

    private static void fillCheckPanel(JPanel panel, JCheckBox... boxes) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (JCheckBox b : boxes) {
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(b);
        }
        panel.add(Box.createVerticalGlue());
    }

    private JPanel buildAdvancedTab() {
        JPanel adv = new JPanel(new GridBagLayout());
        adv.setBorder(new EmptyBorder(UIScale.scale(new Insets(4, 6, 4, 6))));
        GridBagConstraints c = new GridBagConstraints();
        for (int row = 0; row < ROWS; row++) {
            rowLabels[row] = new JLabel();
            arrows[row] = new JLabel(">>");
            fromFields[row] = createRangeField("0");
            toFields[row] = createRangeField("255");

            int top = row == 0 ? 0 : 3;
            c.gridy = row;
            c.anchor = GridBagConstraints.LINE_START;
            c.gridx = 0;
            c.insets = UIScale.scale(new Insets(top, 0, 0, 5));
            adv.add(rowLabels[row], c);
            c.gridx = 1;
            c.insets = UIScale.scale(new Insets(top, 0, 0, 0));
            adv.add(fromFields[row], c);
            c.gridx = 2;
            c.insets = UIScale.scale(new Insets(top, 6, 0, 6));
            adv.add(arrows[row], c);
            c.gridx = 3;
            c.insets = UIScale.scale(new Insets(top, 0, 0, 0));
            adv.add(toFields[row], c);
        }
        // keep the rows in the top-left corner when the Simple tab is larger
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 4;
        filler.gridy = ROWS;
        filler.weightx = 1;
        filler.weighty = 1;
        adv.add(Box.createGlue(), filler);
        return adv;
    }

    private static JTextField createRangeField(String text) {
        JTextField field = new JTextField(text, FIELD_MAX_LENGTH);
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new MaxLengthFilter(FIELD_MAX_LENGTH));
        return field;
    }

    /** Focus order of the designer's TabIndex values. */
    private void installTabOrder() {
        setFocusTraversalPolicy(new TabIndexPolicy(List.of(rgbRadio, hsvRadio, grayChk, websafeChk,
                generateButton, selectButton, tabControl, redChk, greenChk, blueChk, satChk, valueChk,
                fromFields[0], toFields[0], fromFields[1], toFields[1], fromFields[2], toFields[2],
                cancelBtn)));
    }

    // ---------------------------------------------------------------- events

    private void wireEvents() {
        rgbRadio.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                onOptionsChanged();
            }
        });
        hsvRadio.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                onOptionsChanged();
            }
        });
        grayChk.addItemListener(e -> onOptionsChanged());
        tabControl.addChangeListener(e -> onOptionsChanged());

        for (int row = 0; row < ROWS; row++) {
            addValidation(fromFields[row]);
            addValidation(toFields[row]);
        }

        colorPanel.addPropertyChangeListener("color",
                e -> selectButton.setEnabled(colorPanel.getColor() != null));
        generateButton.addActionListener(e -> generate());
        selectButton.addActionListener(e -> {
            Color c = colorPanel.getColor();
            if (c != null) {
                App.colorManager().setRGB(c);
            }
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());
    }

    private void addValidation(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateField(field);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateField(field);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // attribute changes only
            }
        });
    }

    /**
     * Colour space, grayscale or tab changed ({@code RadioButton1_CheckedChanged}
     * and {@code TabControl1_SelectedIndexChanged}).
     */
    private void onOptionsChanged() {
        boolean gray = grayChk.isSelected();
        setEnabledDeep(rgbPnl, !gray && rgbRadio.isSelected());
        setEnabledDeep(hsvPnl, !gray && hsvRadio.isSelected());
        for (int row = 1; row < ROWS; row++) {
            rowLabels[row].setEnabled(!gray);
            fromFields[row].setEnabled(!gray);
            arrows[row].setEnabled(!gray);
            toFields[row].setEnabled(!gray);
        }
        updateRowLabels();
        mode = tabControl.getSelectedIndex();
        revalidateAll();
    }

    private static void setEnabledDeep(JComponent panel, boolean enabled) {
        panel.setEnabled(enabled);
        for (Component child : panel.getComponents()) {
            child.setEnabled(enabled);
        }
    }

    // ------------------------------------------------------------ validation

    /** Same order as the original's six {@code txt_TextChanged} calls. */
    private void revalidateAll() {
        for (JTextField f : fromFields) {
            validateField(f);
        }
        for (JTextField f : toFields) {
            validateField(f);
        }
    }

    /**
     * Port of {@code txt_TextChanged}: empty, non-numeric or out-of-range text
     * is Tomato; a "from" above its "to" (or a "to" below its "from") is
     * LemonChiffon on the edited field; a field that becomes valid
     * re-validates its LemonChiffon partner.
     */
    private void validateField(JTextField field) {
        int row = rowOf(field);
        boolean isFrom = fromFields[row] == field;
        JTextField other = isFrom ? toFields[row] : fromFields[row];

        Integer value = NumericInput.parseInRange(field.getText(), 0, maxOf(row));
        if (value == null) {
            field.setBackground(UiUtil.TOMATO);
        } else {
            Long otherValue = NumericInput.parseUnsigned(other.getText());
            boolean ordered = otherValue == null || (isFrom ? value <= otherValue : value >= otherValue);
            if (ordered) {
                field.setBackground(UiUtil.windowBackground());
                if (UiUtil.LEMON_CHIFFON.equals(other.getBackground())) {
                    validateField(other);
                }
            } else {
                field.setBackground(UiUtil.LEMON_CHIFFON);
            }
        }
        setValid(computeValid());
    }

    private int rowOf(JTextField field) {
        for (int row = 0; row < ROWS; row++) {
            if (fromFields[row] == field || toFields[row] == field) {
                return row;
            }
        }
        throw new IllegalArgumentException("Not a range field");
    }

    /** 255 for RGB, 360 for H, 100 for S/V and for the grayscale level (percent). */
    private int maxOf(int row) {
        if (row == 0) {
            return grayChk.isSelected() ? 100 : hsvRadio.isSelected() ? 360 : 255;
        }
        return hsvRadio.isSelected() ? 100 : 255;
    }

    /** {@code getValid}: no field in use is Tomato or LemonChiffon. */
    private boolean computeValid() {
        int rowsInUse = grayChk.isSelected() ? 1 : ROWS;
        for (int row = 0; row < rowsInUse; row++) {
            if (isFlagged(fromFields[row]) || isFlagged(toFields[row])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFlagged(JTextField f) {
        Color bg = f.getBackground();
        return UiUtil.TOMATO.equals(bg) || UiUtil.LEMON_CHIFFON.equals(bg);
    }

    /** {@code setValid}: Generate needs valid ranges in the Advanced tab only. */
    private void setValid(boolean valid) {
        generateButton.setEnabled(mode != ADVANCED_TAB || valid);
    }

    // ------------------------------------------------------------ generation

    /** Port of {@code Button1_Click} (Generate), with inclusive upper bounds. */
    private void generate() {
        boolean simple = tabControl.getSelectedIndex() == SIMPLE_TAB;
        Color c;
        if (grayChk.isSelected()) {
            int level;
            if (simple) {
                level = VB.nextInclusive(random, 0, 255);
            } else {
                Integer percent = randomInRange(0);
                if (percent == null) {
                    UiUtil.beep();
                    return;
                }
                level = VB.cint(percent / 100.0 * 255);
            }
            c = ColorChanger.grayscaleColor(level);
        } else if (rgbRadio.isSelected()) {
            int r;
            int g;
            int b;
            if (simple) {
                r = VB.nextInclusive(random, redChk.isSelected() ? 127 : 0, 255);
                g = VB.nextInclusive(random, greenChk.isSelected() ? 127 : 0, 255);
                b = VB.nextInclusive(random, blueChk.isSelected() ? 127 : 0, 255);
            } else {
                Integer rr = randomInRange(0);
                Integer rg = randomInRange(1);
                Integer rb = randomInRange(2);
                if (rr == null || rg == null || rb == null) {
                    UiUtil.beep();
                    return;
                }
                r = rr;
                g = rg;
                b = rb;
            }
            c = new Color(r, g, b);
        } else {
            int h;
            int s;
            int v;
            if (simple) {
                h = VB.nextInclusive(random, 0, 359);
                s = VB.nextInclusive(random, satChk.isSelected() ? 50 : 0, 100);
                v = VB.nextInclusive(random, valueChk.isSelected() ? 50 : 0, 100);
            } else {
                Integer rh = randomInRange(0);
                Integer rs = randomInRange(1);
                Integer rv = randomInRange(2);
                if (rh == null || rs == null || rv == null) {
                    UiUtil.beep();
                    return;
                }
                h = rh;
                s = rs;
                v = rv;
            }
            c = new HSVColor(h, s, v, 255).toColor();
        }
        if (websafeChk.isSelected()) {
            c = ColorChanger.websafeColor(c);
        }
        colorPanel.setColor(c);
    }

    /** Random value in the from/to range of a row, or {@code null} if the range is not valid. */
    private Integer randomInRange(int row) {
        int max = maxOf(row);
        Integer from = NumericInput.parseInRange(fromFields[row].getText(), 0, max);
        Integer to = NumericInput.parseInRange(toFields[row].getText(), 0, max);
        if (from == null || to == null || from > to) {
            return null;
        }
        return VB.nextInclusive(random, from, to);
    }

    // ------------------------------------------------------------------ texts

    /** {@code updateLang}. */
    private void updateLang() {
        setTitle(Lang.get("TOOL_ADVRAND"));
        outputLabel.setText(Lang.get("COMMON", "OUTPUT"));
        generateButton.setText(Lang.get("TOOL_ADVRAND", "GENERATE"));
        selectButton.setText(Lang.get("COMMON", "SELECTANDCLOSE"));
        cancelBtn.setText(Lang.get("COMMON", "CANCEL"));
        rgbRadio.setText(Lang.get("RGB"));
        hsvRadio.setText(Lang.get("HSV"));
        redChk.setText(Lang.get("TOOL_ADVRAND", "HIGHRED"));
        greenChk.setText(Lang.get("TOOL_ADVRAND", "HIGHGREEN"));
        blueChk.setText(Lang.get("TOOL_ADVRAND", "HIGHBLUE"));
        satChk.setText(Lang.get("TOOL_ADVRAND", "HIGHSATURATION"));
        valueChk.setText(Lang.get("TOOL_ADVRAND", "HIGHVALUE"));
        grayChk.setText(Lang.get("TOOL_ADVRAND", "GRAYSCALE"));
        websafeChk.setText(Lang.get("TOOL_ADVRAND", "WEBSAFE"));
        tabControl.setTitleAt(SIMPLE_TAB, Lang.get("TOOL_ADVRAND", "SIMPLE"));
        tabControl.setTitleAt(ADVANCED_TAB, Lang.get("TOOL_ADVRAND", "ADVANCED"));
        updateRowLabels();
        fixRowLabelWidth();
        if (isDisplayable()) {
            pack();
        }
    }

    /** Row labels R/G/B or H/S/V; the grayscale row is the HSV value (V). */
    private void updateRowLabels() {
        boolean rgb = rgbRadio.isSelected();
        rowLabels[0].setText(grayChk.isSelected() ? Lang.get("HSV", "V")
                : rgb ? Lang.get("RGB", "R") : Lang.get("HSV", "H"));
        rowLabels[1].setText(rgb ? Lang.get("RGB", "G") : Lang.get("HSV", "S"));
        rowLabels[2].setText(rgb ? Lang.get("RGB", "B") : Lang.get("HSV", "V"));
    }

    /** Same width for every possible row label, so the fields do not move when switching RGB/HSV. */
    private void fixRowLabelWidth() {
        FontMetrics fm = rowLabels[0].getFontMetrics(rowLabels[0].getFont());
        int width = 0;
        for (String key : new String[] {"R", "G", "B"}) {
            width = Math.max(width, fm.stringWidth(Lang.get("RGB", key)));
        }
        for (String key : new String[] {"H", "S", "V"}) {
            width = Math.max(width, fm.stringWidth(Lang.get("HSV", key)));
        }
        for (JLabel label : rowLabels) {
            label.setPreferredSize(new Dimension(width, fm.getHeight()));
        }
    }

    /**
     * Swing's usual focus rules (one stop per radio group, like WinForms) but
     * in a fixed order, limited to the listed controls.
     */
    private static final class TabIndexPolicy extends LayoutFocusTraversalPolicy {
        private static final long serialVersionUID = 1L;
        private final transient List<Component> order;

        TabIndexPolicy(List<Component> order) {
            this.order = order;
            setComparator(Comparator.comparingInt(comp -> {
                int i = order.indexOf(comp);
                return i < 0 ? order.size() : i;
            }));
        }

        @Override
        protected boolean accept(Component comp) {
            return order.contains(comp) && super.accept(comp);
        }
    }

    /** {@code TextBox.MaxLength}: typed or pasted text is cut to the maximum length. */
    private static final class MaxLengthFilter extends DocumentFilter {
        private final int maxLength;

        MaxLengthFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String t = text == null ? "" : text;
            int room = maxLength - (fb.getDocument().getLength() - length);
            if (t.length() > room) {
                t = t.substring(0, Math.max(0, room));
            }
            fb.replace(offset, length, t, attrs);
        }
    }
}
