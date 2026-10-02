package colorpicker.ui;

import colorpicker.App;
import colorpicker.core.ColorChanger;
import colorpicker.core.ColorFilter;
import colorpicker.core.ColorManager;
import colorpicker.core.HSVColor;
import colorpicker.core.NumericInput;
import colorpicker.core.RGBColor;
import colorpicker.core.VB;
import colorpicker.i18n.Lang;
import colorpicker.i18n.Language;
import colorpicker.settings.AppSettings;
import colorpicker.ui.components.ColorSwatch;
import colorpicker.ui.components.GradientSlider;
import colorpicker.ui.tools.AdvancedRandomColorDialog;
import colorpicker.ui.tools.ColorCombinerDialog;
import colorpicker.ui.tools.ImageColorsDialog;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.ScaledImageIcon;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * The main window (port of {@code Form1}): RGB and HSV sliders, filters,
 * other tools, settings and credits tabs, the colour box with its hexadecimal
 * and decimal codes, and the screen picker / websafe / random colour buttons.
 *
 * <p>Every widget follows the shared {@link ColorManager}
 * ({@code colormanager_ColorChanged}). Text written by the code is never taken
 * as user input, and a field is not rewritten while its own document is being
 * edited by the user; it is re-synchronised with the colour when it loses focus.
 */
public class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final int MAX_DEC_COLOR = 16777215;
    // Lengths below are at 100 % and go through UIScale.scale (large desktop fonts).
    /** Viewport height asked for by the scrolling tab contents, so they never make the window taller. */
    private static final int SCROLL_VIEWPORT_HEIGHT = 60;
    private static final int DESC_GAP = 8;
    private static final Insets TAB_INSETS = new Insets(6, 8, 6, 8);

    /** Filter buttons in the designer's top-to-bottom order. */
    private static final ColorFilter[] FILTER_ORDER = {
        ColorFilter.GRAYSCALE, ColorFilter.NEGATIVE, ColorFilter.INTENSIFY, ColorFilter.SEPIA,
        ColorFilter.BRIGHTER, ColorFilter.DARKER, ColorFilter.RED_ONLY, ColorFilter.GREEN_ONLY,
        ColorFilter.BLUE_ONLY, ColorFilter.RGB_SHUFFLE,
    };
    /** Language keys of the "Other tools" buttons, in the designer's order. */
    private static final String[] TOOL_KEYS = {"COMBINER", "ADVANCEDRANDOM", "ALLCOLORSIMAGE"};

    private final transient ColorManager colorManager = App.colorManager();
    private final transient AppSettings settings = AppSettings.get();
    private final transient Random random = new Random();

    /** True while the code (not the user) writes into the text fields. */
    private boolean updatingFields;
    /** Field whose own document change is being handled; it must not be rewritten meanwhile. */
    private JTextField typingField;
    private String hoverFilterText = "";
    private String hoverToolText = "";

    private final JTabbedPane tabs = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final Map<Component, String> tabTitles = new HashMap<>();

    // RGB tab
    private final JLabel rLabel = new JLabel();
    private final JLabel gLabel = new JLabel();
    private final JLabel bLabel = new JLabel();
    private final GradientSlider redBar = new GradientSlider(0, 255);
    private final GradientSlider greenBar = new GradientSlider(0, 255);
    private final GradientSlider blueBar = new GradientSlider(0, 255);
    private final InputField redTxt = new InputField(3, "255", false, false);
    private final InputField greenTxt = new InputField(3, "255", false, false);
    private final InputField blueTxt = new InputField(3, "255", false, false);
    private final JComponent rgbTab;

    // HSV tab
    private final JLabel hLabel = new JLabel();
    private final JLabel sLabel = new JLabel();
    private final JLabel vLabel = new JLabel();
    private final GradientSlider hueBar = new GradientSlider(0, 360);
    private final GradientSlider saturationBar = new GradientSlider(0, 100);
    private final GradientSlider valueBar = new GradientSlider(0, 100);
    private final InputField hueTxt = new InputField(3, "360", false, false);
    private final InputField saturationTxt = new InputField(3, "360", false, false);
    private final InputField valueTxt = new InputField(3, "360", false, false);
    private final JComponent hsvTab;

    // Filters tab
    private final Map<ColorFilter, JButton> filterButtons = new EnumMap<>(ColorFilter.class);
    private final JLabel filterLabel = new JLabel();
    private final DescriptionArea filterDesc = new DescriptionArea();
    private final JScrollPane filterColumn;
    private final JComponent filtersTab;

    // Other tools tab
    private final JButton[] toolButtons = new JButton[TOOL_KEYS.length];
    private final JLabel toolLabel = new JLabel();
    private final DescriptionArea toolDesc = new DescriptionArea();
    private final JScrollPane toolColumn;
    private final JComponent toolsTab;

    // Settings tab
    private final JLabel langLabel = sectionLabel();
    private final JComboBox<Language> langCombo = new JComboBox<>(Language.values());
    private final JButton langSet = new JButton();
    private final JLabel spaceLabel = sectionLabel();
    private final JCheckBox rgbChk = new JCheckBox();
    private final JCheckBox hsvChk = new JCheckBox();
    private final JLabel speedLabel = sectionLabel();
    private final JCheckBox speedChk = new JCheckBox();
    private final JLabel clipLabel = sectionLabel();
    private final JCheckBox clipChk = new JCheckBox();
    private final JScrollPane settingsTab;

    // colour box, codes and the bottom row
    private final ColorSwatch colorPanel = new ColorSwatch(Color.BLACK, UIScale.scale(80), UIScale.scale(80));
    private final JPopupMenu copyStrip = new JPopupMenu();
    private final JMenuItem copyVbNetItem = new JMenuItem();
    private final JMenuItem copyWinFormsItem = new JMenuItem();
    private final JMenuItem copyCssItem = new JMenuItem();
    private final JMenuItem copyHexForVbItem = new JMenuItem();
    private final JMenuItem copyHexItem = new JMenuItem();
    private final JMenuItem copyHsvItem = new JMenuItem();
    private final JLabel hexLabel = new JLabel("#");
    private final InputField hexTxt = new InputField(6, "DDDDDD", true, true);
    private final JLabel decLabel = new JLabel("Dec");
    private final InputField decTxt = new InputField(8, "16777215", false, false);
    private final JButton colorPickerBtn = iconButton("colorPickerBtn");
    private final JButton webColorBtn = iconButton("websafecoloricon");
    private final JButton randomColorBtn = iconButton("randomColorBtn");

    public MainWindow() {
        super("ColorPicker");
        setIconImages(Resources.appIcons());
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        // InfoPopup / WebsitePopup timings
        ToolTipManager.sharedInstance().setInitialDelay(200);
        ToolTipManager.sharedInstance().setReshowDelay(100);
        ToolTipManager.sharedInstance().setDismissDelay(5000);

        rgbTab = buildSliderTab(new JLabel[] {rLabel, gLabel, bLabel},
                new GradientSlider[] {redBar, greenBar, blueBar}, new InputField[] {redTxt, greenTxt, blueTxt});
        hsvTab = buildSliderTab(new JLabel[] {hLabel, sLabel, vLabel},
                new GradientSlider[] {hueBar, saturationBar, valueBar},
                new InputField[] {hueTxt, saturationTxt, valueTxt});
        hueBar.setGradientImage(GradientSlider.hueSpectrum());

        List<JButton> filters = new ArrayList<>();
        for (ColorFilter f : FILTER_ORDER) {
            JButton b = new JButton();
            filterButtons.put(f, b);
            filters.add(b);
        }
        filterColumn = buttonColumn(filters, 4);
        filtersTab = buildDescribedTab(filterColumn, filterLabel, filterDesc);

        for (int i = 0; i < toolButtons.length; i++) {
            toolButtons[i] = new JButton();
        }
        toolColumn = buttonColumn(List.of(toolButtons), 6);
        toolsTab = buildDescribedTab(toolColumn, toolLabel, toolDesc);

        settingsTab = buildSettingsTab();

        setupTabs();
        setupCopyStrip();
        setContentPane(buildContent());
        wireEvents();
        loadSettings();
        updateLang();

        colorManager.addColorChangedListener(this::onColorChanged);
        Lang.addListener(this::onLanguageChanged);
        colorManager.setRGB(initialColor());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                // hexTxt has the lowest TabIndex of the original form, so it starts focused.
                hexTxt.requestFocusInWindow();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                closeApplication();
            }
        });
        pack();
        centerOnPointerScreen();
    }

    /** {@code StartPosition = CenterScreen}: centred on the monitor that has the mouse pointer. */
    private void centerOnPointerScreen() {
        try {
            PointerInfo pointer = MouseInfo.getPointerInfo();
            if (pointer != null) {
                GraphicsConfiguration gc = pointer.getDevice().getDefaultConfiguration();
                Rectangle r = gc.getBounds();
                Insets si = Toolkit.getDefaultToolkit().getScreenInsets(gc);
                if (r.width - si.left - si.right > 0 && r.height - si.top - si.bottom > 0) {
                    r = new Rectangle(r.x + si.left, r.y + si.top, r.width - si.left - si.right,
                            r.height - si.top - si.bottom);
                }
                setLocation(r.x + Math.max(0, (r.width - getWidth()) / 2),
                        r.y + Math.max(0, (r.height - getHeight()) / 2));
                return;
            }
        } catch (HeadlessException | SecurityException e) {
            // no pointer information: centre on the default screen below
        }
        setLocationRelativeTo(null);
    }

    /** Restores, shows and focuses the window (used by single-instance activation). */
    public void bringToFront() {
        int state = getExtendedState();
        if ((state & ICONIFIED) != 0) {
            setExtendedState(state & ~ICONIFIED);
        }
        setVisible(true);
        toFront();
        requestFocus();
    }

    // ================================================================== layout

    private void setupTabs() {
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_HAS_FULL_BORDER, true);
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_HEIGHT, 26);
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_INSETS, new Insets(2, 8, 2, 8));
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_PLACEMENT,
                FlatClientProperties.TABBED_PANE_PLACEMENT_TRAILING);
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TABS_POPUP_POLICY,
                FlatClientProperties.TABBED_PANE_POLICY_NEVER);
        for (JComponent tab : new JComponent[] {rgbTab, hsvTab, filtersTab, toolsTab, settingsTab}) {
            tabs.addTab("", tab);
        }
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(UIScale.scale(new Insets(10, 10, 6, 10))));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        content.add(tabs, c);

        // The colour column is as tall as the tabs; the colour box takes the free height.
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = UIScale.scale(new Insets(0, 6, 0, 0));
        content.add(buildColorColumn(), c);

        JPanel buttons = new JPanel(new GridLayout(1, 3, UIScale.scale(2), 0));
        buttons.add(colorPickerBtn);
        buttons.add(webColorBtn);
        buttons.add(randomColorBtn);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = UIScale.scale(new Insets(4, 6, 0, 0));
        content.add(buttons, c);
        return content;
    }

    /** Colour box, "#" hex code and "Dec" code (right-hand column of the form). */
    private JPanel buildColorColumn() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        p.add(colorPanel, c);
        addCodeRow(p, 1, hexLabel, hexTxt, 6);
        addCodeRow(p, 2, decLabel, decTxt, 4);
        return p;
    }

    private static void addCodeRow(JPanel p, int row, JLabel label, JTextField field, int top) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.LINE_END;
        c.insets = UIScale.scale(new Insets(top, 0, 0, 4));
        p.add(label, c);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = UIScale.scale(new Insets(top, 0, 0, 0));
        p.add(field, c);
        label.setLabelFor(field);
    }

    /** RGB / HSV tab: three rows of label, gradient slider and value field. */
    private static JPanel buildSliderTab(JLabel[] labels, GradientSlider[] sliders, InputField[] fields) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(tabBorder());
        for (int i = 0; i < labels.length; i++) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = i;
            c.weighty = 1;
            c.gridx = 0;
            c.insets = UIScale.scale(new Insets(0, 2, 0, 4));
            p.add(labels[i], c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = UIScale.scale(new Insets(0, 0, 0, 4));
            p.add(sliders[i], c);
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(0, 0, 0, 0);
            p.add(fields[i], c);
            labels[i].setLabelFor(fields[i]);
        }
        return p;
    }

    /** Filters / Other tools tab: scrolling button column, bold title and a wrapping description. */
    private static JPanel buildDescribedTab(JScrollPane column, JLabel title, DescriptionArea desc) {
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(tabBorder());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 2;
        c.fill = GridBagConstraints.VERTICAL;
        c.weighty = 1;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        p.add(column, c);

        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.insets = UIScale.scale(new Insets(0, DESC_GAP, 2, 0));
        p.add(title, c);
        c.gridy = 1;
        c.weighty = 1;
        c.insets = UIScale.scale(new Insets(0, DESC_GAP, 0, 0));
        p.add(desc, c);
        return p;
    }

    /** Vertical button column in an auto-scrolling panel ({@code filtersPanel} / {@code tool_panel}). */
    private static JScrollPane buttonColumn(List<JButton> buttons, int gap) {
        ScrollablePanel panel = new ScrollablePanel(new GridLayout(0, 1, 0, UIScale.scale(gap)));
        for (JButton b : buttons) {
            panel.add(b);
            b.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    b.scrollRectToVisible(new Rectangle(b.getSize()));
                }
            });
        }
        return scroller(panel);
    }

    private JScrollPane buildSettingsTab() {
        ScrollablePanel p = new ScrollablePanel(new GridBagLayout());
        p.setBorder(tabBorder());

        JPanel langRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        langRow.setOpaque(false);
        langRow.add(langCombo);
        langRow.add(Box.createHorizontalStrut(UIScale.scale(6)));
        langRow.add(langSet);

        JPanel spaceRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        spaceRow.setOpaque(false);
        spaceRow.add(rgbChk);
        spaceRow.add(Box.createHorizontalStrut(UIScale.scale(10)));
        spaceRow.add(hsvChk);

        Component[] rows = {langLabel, langRow, spaceLabel, spaceRow, speedLabel, speedChk, clipLabel, clipChk};
        for (int i = 0; i < rows.length; i++) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = i;
            c.weightx = 1;
            c.anchor = GridBagConstraints.FIRST_LINE_START;
            // a section label starts each pair of rows
            c.insets = UIScale.scale(new Insets(i == 0 ? 0 : i % 2 == 0 ? 8 : 3, 0, 0, 0));
            if (i == rows.length - 1) {
                c.weighty = 1;
            }
            p.add(rows[i], c);
        }
        return scroller(p);
    }

    private void setupCopyStrip() {
        // Designer order of CopyStrip
        for (JMenuItem item : new JMenuItem[] {copyVbNetItem, copyWinFormsItem, copyCssItem,
            copyHexForVbItem, copyHexItem, copyHsvItem}) {
            copyStrip.add(item);
        }
        colorPanel.setComponentPopupMenu(copyStrip);
    }

    // ================================================================== events

    private void wireEvents() {
        // RGB / HSV sliders (bar_Scroll, pic_MouseDown/MouseMove, hsvbar_Scroll, hsvpic_*)
        for (GradientSlider s : new GradientSlider[] {redBar, greenBar, blueBar}) {
            s.addUserChangeListener(e -> colorManager.setRGB(
                    new RGBColor(redBar.getValue(), greenBar.getValue(), blueBar.getValue())));
        }
        for (GradientSlider s : new GradientSlider[] {hueBar, saturationBar, valueBar}) {
            s.addUserChangeListener(e -> colorManager.setHSV(
                    new HSVColor(hueBar.getValue(), saturationBar.getValue(), valueBar.getValue(), 255)));
        }

        // text boxes (txt_TextChanged, hsvtxt_TextChanged, TextBox1_TextChanged, TextBox2_TextChanged)
        for (InputField f : new InputField[] {redTxt, greenTxt, blueTxt}) {
            onUserEdit(f, () -> onRgbTyped(f));
        }
        for (InputField f : new InputField[] {hueTxt, saturationTxt, valueTxt}) {
            onUserEdit(f, () -> onHsvTyped(f));
        }
        onUserEdit(hexTxt, this::onHexTyped);
        onUserEdit(decTxt, this::onDecTyped);

        // colour box
        colorPanel.setDropHandler(colorManager::setRGB);
        copyVbNetItem.addActionListener(e -> {
            RGBColor c = colorManager.getRGB();
            UiUtil.setClipboardText("Color.FromArgb(" + c.r() + ", " + c.g() + ", " + c.b() + ")");
        });
        copyWinFormsItem.addActionListener(e -> {
            RGBColor c = colorManager.getRGB();
            UiUtil.setClipboardText(c.r() + "; " + c.g() + "; " + c.b());
        });
        copyCssItem.addActionListener(e -> {
            RGBColor c = colorManager.getRGB();
            UiUtil.setClipboardText("rgb(" + c.r() + ", " + c.g() + ", " + c.b() + ")");
        });
        copyHexForVbItem.addActionListener(e -> {
            RGBColor c = colorManager.getRGB();
            UiUtil.setClipboardText("&H00" + VB.hex2(c.b()) + VB.hex2(c.g()) + VB.hex2(c.r()));
        });
        copyHexItem.addActionListener(e -> {
            RGBColor c = colorManager.getRGB();
            UiUtil.setClipboardText("0x" + VB.hex2(c.b()) + VB.hex2(c.g()) + VB.hex2(c.r()));
        });
        copyHsvItem.addActionListener(e -> {
            HSVColor c = colorManager.getHSV();
            UiUtil.setClipboardText("hsv(" + c.h() + ", " + c.s() + ", " + c.v() + ")");
        });

        // bottom row
        colorPickerBtn.addActionListener(e -> ScreenColorPicker.start(this));
        // explicit: FlatLaf derives no disabled icon from a ScaledImageIcon
        webColorBtn.setDisabledIcon(scaledIcon("websafecoloricongray"));
        webColorBtn.addActionListener(e -> colorManager.setRGB(ColorChanger.websafeColor(colorManager.getColor())));
        webColorBtn.setFocusable(false); // TabStop = False in the designer
        randomColorBtn.addActionListener(e -> colorManager.setRGB(new Color(VB.nextInclusive(random, 0, 255),
                VB.nextInclusive(random, 0, 255), VB.nextInclusive(random, 0, 255))));

        // filters
        for (ColorFilter f : FILTER_ORDER) {
            JButton b = filterButtons.get(f);
            b.addActionListener(e -> colorManager.setRGB(f.apply(colorManager.getColor())));
            String key = f.name().replace("_", "");
            installHoverDescription(b, filterColumn, filterDesc, () -> Lang.get("FILTER_DESC", key),
                    () -> hoverFilterText);
        }
        installLeaveReset(filterColumn, filterDesc, () -> hoverFilterText);

        // other tools
        toolButtons[0].addActionListener(e -> ColorCombinerDialog.showDialog(this));
        toolButtons[1].addActionListener(e -> AdvancedRandomColorDialog.showDialog(this));
        toolButtons[2].addActionListener(e -> ImageColorsDialog.showDialog(this));
        for (int i = 0; i < toolButtons.length; i++) {
            String key = TOOL_KEYS[i];
            installHoverDescription(toolButtons[i], toolColumn, toolDesc, () -> Lang.get("TOOL_DESC", key),
                    () -> hoverToolText);
        }
        installLeaveReset(toolColumn, toolDesc, () -> hoverToolText);

        // settings
        langCombo.addActionListener(e -> langSet.setEnabled(langCombo.getSelectedItem() != savedLanguage()));
        langSet.addActionListener(e -> applySelectedLanguage());
        rgbChk.addActionListener(e -> onColorSpaceToggled(rgbChk));
        hsvChk.addActionListener(e -> onColorSpaceToggled(hsvChk));
        speedChk.addActionListener(e -> {
            settings.setOptimizeSpeed(speedChk.isSelected());
            settings.save();
        });
        clipChk.addActionListener(e -> {
            settings.setClipCopy(clipChk.isSelected());
            settings.save();
        });
    }

    /** Treats document changes that the code did not make as typing, and re-syncs the field on focus loss. */
    private void onUserEdit(InputField field, Runnable handler) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // attribute changes only
            }

            private void edited() {
                if (!updatingFields) {
                    handler.run();
                }
            }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!e.isTemporary()) {
                    resync(field);
                }
            }
        });
    }

    /** {@code txt_TextChanged}: R, G or B typed. */
    private void onRgbTyped(InputField field) {
        if (NumericInput.parseInRange(field.getText(), 0, 255) == null) {
            field.setError(true);
            return;
        }
        field.setError(false);
        Integer r = typedValue(redTxt, 255);
        Integer g = typedValue(greenTxt, 255);
        Integer b = typedValue(blueTxt, 255);
        if (r != null && g != null && b != null) {
            applyTyped(field, () -> colorManager.setRGB(new RGBColor(r, g, b)));
        }
    }

    /** {@code hsvtxt_TextChanged}: H, S or V typed. */
    private void onHsvTyped(InputField field) {
        if (NumericInput.parseInRange(field.getText(), 0, field == hueTxt ? 360 : 100) == null) {
            field.setError(true);
            return;
        }
        field.setError(false);
        Integer h = typedValue(hueTxt, 360);
        Integer s = typedValue(saturationTxt, 100);
        Integer v = typedValue(valueTxt, 100);
        if (h != null && s != null && v != null) {
            applyTyped(field, () -> colorManager.setHSV(new HSVColor(h, s, v, 255)));
        }
    }

    /** Value of a field that is not marked invalid, else {@code null}. */
    private static Integer typedValue(InputField f, int max) {
        return f.hasError() ? null : NumericInput.parseInRange(f.getText(), 0, max);
    }

    /** {@code TextBox1_TextChanged}: hex code typed, {@code #} optional, 3 or 6 digits. */
    private void onHexTyped() {
        String digits = hexTxt.getText().replace("#", "");
        Color c = ColorChanger.isValidHexColorCode("#" + digits) ? ColorChanger.fromHtml(digits) : null;
        if (c == null) {
            hexTxt.setError(true);
            return;
        }
        hexTxt.setError(false);
        applyTyped(hexTxt, () -> colorManager.setRGB(c));
    }

    /** {@code TextBox2_TextChanged}: decimal code typed (0 - 16777215). */
    private void onDecTyped() {
        Long v = NumericInput.parseUnsigned(decTxt.getText());
        if (v == null || v > MAX_DEC_COLOR) {
            decTxt.setError(true);
            return;
        }
        decTxt.setError(false);
        Color c = ColorChanger.decToColor(v);
        applyTyped(decTxt, () -> colorManager.setRGB(c));
    }

    private void applyTyped(JTextField field, Runnable change) {
        JTextField previous = typingField;
        typingField = field;
        try {
            change.run();
        } finally {
            typingField = previous;
        }
    }

    /** Puts the colour's value back into a field the user left. */
    private void resync(InputField field) {
        boolean previous = updatingFields;
        updatingFields = true;
        try {
            field.setTextIfChanged(modelText(field));
            field.setError(false);
        } finally {
            updatingFields = previous;
        }
    }

    private String modelText(InputField f) {
        RGBColor rgb = colorManager.getRGB();
        HSVColor hsv = colorManager.getHSV();
        if (f == redTxt) {
            return Integer.toString(rgb.r());
        } else if (f == greenTxt) {
            return Integer.toString(rgb.g());
        } else if (f == blueTxt) {
            return Integer.toString(rgb.b());
        } else if (f == hueTxt) {
            return Integer.toString(hsv.h());
        } else if (f == saturationTxt) {
            return Integer.toString(hsv.s());
        } else if (f == valueTxt) {
            return Integer.toString(hsv.v());
        } else if (f == hexTxt) {
            return ColorChanger.toHtml(rgb.r(), rgb.g(), rgb.b()).substring(1);
        }
        return Integer.toString(ColorChanger.hexToDec(rgb.r(), rgb.g(), rgb.b()));
    }

    /** {@code colormanager_ColorChanged}: brings every widget in line with the current colour. */
    private void onColorChanged() {
        RGBColor rgb = colorManager.getRGB();
        HSVColor hsv = colorManager.getHSV();
        int r = rgb.r();
        int g = rgb.g();
        int b = rgb.b();
        Color c = new Color(r, g, b);
        boolean previous = updatingFields;
        updatingFields = true;
        try {
            // updateRGB / updateHSV (sliders and text boxes), then the reset of the backgrounds
            redBar.setValue(r);
            greenBar.setValue(g);
            blueBar.setValue(b);
            hueBar.setValue(hsv.h());
            saturationBar.setValue(hsv.s());
            valueBar.setValue(hsv.v());
            for (InputField f : new InputField[] {redTxt, greenTxt, blueTxt, hueTxt, saturationTxt, valueTxt,
                hexTxt, decTxt}) {
                if (f != typingField) {
                    f.setTextIfChanged(modelText(f));
                    f.setError(false);
                }
            }

            // gradients
            saturationBar.setGradient(new HSVColor(hsv.h(), 0, hsv.v()).toColor(),
                    new HSVColor(hsv.h(), 100, hsv.v()).toColor());
            valueBar.setGradient(Color.BLACK, new HSVColor(hsv.h(), hsv.s(), 100).toColor());
            redBar.setGradient(new Color(0, g, b), new Color(255, g, b));
            greenBar.setGradient(new Color(r, 0, b), new Color(r, 255, b));
            blueBar.setGradient(new Color(r, g, 0), new Color(r, g, 255));

            colorPanel.setColor(c);

            // filters
            boolean notGray = !(r == b && r == g);
            filterButtons.get(ColorFilter.GRAYSCALE).setEnabled(notGray);
            filterButtons.get(ColorFilter.BRIGHTER).setEnabled(!(r == 255 && b == 255 && g == 255));
            filterButtons.get(ColorFilter.DARKER).setEnabled(!(r == 0 && b == 0 && g == 0));
            filterButtons.get(ColorFilter.INTENSIFY).setEnabled(
                    !(r == 255 || b == 255 || g == 255 || (r == 0 && b == 0 && g == 0)));
            filterButtons.get(ColorFilter.RED_ONLY).setEnabled(r > 0 && (b > 0 || g > 0));
            filterButtons.get(ColorFilter.GREEN_ONLY).setEnabled(g > 0 && (b > 0 || r > 0));
            filterButtons.get(ColorFilter.BLUE_ONLY).setEnabled(b > 0 && (r > 0 || g > 0));
            filterButtons.get(ColorFilter.RGB_SHUFFLE).setEnabled(notGray);
            filterButtons.get(ColorFilter.SEPIA).setEnabled(!ColorFilter.SEPIA.apply(c).equals(c));

            // websafe button (the disabled icon is websafecoloricongray)
            webColorBtn.setEnabled(!ColorChanger.isWebsafeColor(c));
        } finally {
            updatingFields = previous;
        }
    }

    // ================================================================== settings

    /** {@code Form1_Load}: settings to the check boxes, hidden colour-space tabs. */
    private void loadSettings() {
        speedChk.setSelected(settings.isOptimizeSpeed());
        rgbChk.setSelected(settings.isUseRGB());
        hsvChk.setSelected(settings.isUseHSV());
        clipChk.setSelected(settings.isClipCopy());
        updateColorSpaceChecks();
        applyColorSpaceTabs();
        tabs.setSelectedIndex(0);
    }

    /** {@code CheckBox_CheckedChanged} of the RGB / HSV check boxes. */
    private void onColorSpaceToggled(JCheckBox source) {
        settings.setUseRGB(rgbChk.isSelected());
        settings.setUseHSV(hsvChk.isSelected());
        settings.save();
        updateColorSpaceChecks();
        applyColorSpaceTabs();
        tabs.setSelectedComponent(settingsTab);
        source.requestFocusInWindow();
    }

    /** The only checked colour space cannot be unchecked. */
    private void updateColorSpaceChecks() {
        rgbChk.setEnabled(hsvChk.isSelected());
        hsvChk.setEnabled(rgbChk.isSelected());
    }

    private void applyColorSpaceTabs() {
        if (rgbChk.isSelected()) {
            showTab(rgbTab, 0);
        } else {
            hideTab(rgbTab);
        }
        if (hsvChk.isSelected()) {
            showTab(hsvTab, tabs.indexOfComponent(rgbTab) >= 0 ? 1 : 0);
        } else {
            hideTab(hsvTab);
        }
    }

    private void showTab(Component tab, int index) {
        if (tabs.indexOfComponent(tab) < 0) {
            tabs.insertTab(tabTitles.getOrDefault(tab, ""), null, tab, null, Math.min(index, tabs.getTabCount()));
        }
    }

    private void hideTab(Component tab) {
        int i = tabs.indexOfComponent(tab);
        if (i >= 0) {
            tabs.removeTabAt(i);
        }
    }

    private void setTabTitle(Component tab, String title) {
        tabTitles.put(tab, title);
        int i = tabs.indexOfComponent(tab);
        if (i >= 0) {
            tabs.setTitleAt(i, title);
        }
    }

    private Language savedLanguage() {
        Language l = Language.fromCode(settings.getLanguage());
        return l != null ? l : Language.ENGLISH;
    }

    /** {@code Button7_Click} (langSet). */
    private void applySelectedLanguage() {
        Language selected = (Language) langCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        settings.setLanguage(selected.code());
        settings.save();
        langSet.setEnabled(false);
        Lang.load(selected);
    }

    private void onLanguageChanged() {
        if (SwingUtilities.isEventDispatchThread()) {
            updateLang();
        } else {
            SwingUtilities.invokeLater(this::updateLang);
        }
    }

    /** {@code updateLang} + {@code InitializeLanguage}: every text of the window. */
    private void updateLang() {
        setTabTitle(rgbTab, Lang.get("RGB"));
        rLabel.setText(Lang.get("RGB", "R"));
        gLabel.setText(Lang.get("RGB", "G"));
        bLabel.setText(Lang.get("RGB", "B"));

        setTabTitle(hsvTab, Lang.get("HSV"));
        hLabel.setText(Lang.get("HSV", "H"));
        sLabel.setText(Lang.get("HSV", "S"));
        vLabel.setText(Lang.get("HSV", "V"));

        setTabTitle(filtersTab, Lang.get("FILTERS"));
        for (ColorFilter f : FILTER_ORDER) {
            filterButtons.get(f).setText(Lang.get("FILTERS", f.name().replace("_", "")));
        }

        setTabTitle(toolsTab, Lang.get("TOOLS"));
        // tool buttons and both description titles are set by fitToTabWidth()

        hoverFilterText = Lang.get("FILTERS", "HOVER");
        hoverToolText = Lang.get("TOOLS", "HOVER");

        // the section labels and the long check boxes are set by fitToTabWidth()
        setTabTitle(settingsTab, Lang.get("SETTINGS"));
        rgbChk.setText(Lang.get("RGB"));
        hsvChk.setText(Lang.get("HSV"));
        langSet.setText(Lang.get("SETTINGS", "LANGUAGESET"));

        String info = Lang.get("INFO");
        colorPickerBtn.setToolTipText(UiUtil.tooltip(info, Lang.get("INFO", "PICKER")));
        randomColorBtn.setToolTipText(UiUtil.tooltip(info, Lang.get("INFO", "RANDOM")));
        webColorBtn.setToolTipText(UiUtil.tooltip(info, Lang.get("INFO", "WEBSAFE")));
        colorPanel.setToolTipText(UiUtil.tooltip(info, Lang.get("INFO", "COLOR_RIGHTCLICK")));
        speedChk.setToolTipText(UiUtil.tooltip(info, Lang.get("INFO", "SPEED")));
        clipChk.setToolTipText(UiUtil.tooltip(info, Lang.get("INFO", "CLIPBOARD_HEX")));

        copyVbNetItem.setText(Lang.get("FORMATS", "VBNET"));
        copyHexForVbItem.setText(Lang.get("FORMATS", "VBHEX"));
        copyCssItem.setText(Lang.get("FORMATS", "CSS"));
        copyWinFormsItem.setText(Lang.get("FORMATS", "WINFORMS"));
        copyHexItem.setText(Lang.get("FORMATS", "HEX"));
        copyHsvItem.setText(Lang.get("FORMATS", "HSV"));

        // InitializeLanguage
        Language current = Lang.current() != null ? Lang.current() : savedLanguage();
        langCombo.setSelectedItem(current);
        langSet.setEnabled(false);
        filterDesc.setText(hoverFilterText);
        toolDesc.setText(hoverToolText);

        fitToTabWidth();
        if (isDisplayable()) {
            pack();
        }
    }

    /**
     * Keeps the window as narrow as the RGB and HSV tabs allow: long
     * settings and tool button texts are wrapped (WinForms wrapped button text
     * too), and the filter / tool descriptions get the remaining width and the
     * height of their longest text.
     */
    private void fitToTabWidth() {
        int tabWidth = 0;
        for (JComponent tab : new JComponent[] {rgbTab, hsvTab}) {
            tabWidth = Math.max(tabWidth, tab.getPreferredSize().width);
        }
        Insets in = UIScale.scale(TAB_INSETS);
        int textWidth = tabWidth - in.left - in.right - settingsTab.getVerticalScrollBar().getPreferredSize().width;
        langLabel.setText(wrapped(langLabel, Lang.get("SETTINGS", "LANGUAGE"), textWidth, false, true));
        spaceLabel.setText(wrapped(spaceLabel, Lang.get("SETTINGS", "SPACES"), textWidth, false, true));
        speedLabel.setText(wrapped(speedLabel, Lang.get("SETTINGS", "SPEED"), textWidth, false, true));
        clipLabel.setText(wrapped(clipLabel, Lang.get("SETTINGS", "CLIPBOARD"), textWidth, false, true));
        speedChk.setText(wrapped(speedChk, Lang.get("SETTINGS", "OPTIMIZE"),
                textWidth - checkIconWidth(speedChk), false, false));
        clipChk.setText(wrapped(clipChk, Lang.get("SETTINGS", "CLIP_COPY"),
                textWidth - checkIconWidth(clipChk), false, false));

        int descMinimum = filterDesc.getFontMetrics(filterDesc.getFont()).charWidth('n') * 14;
        JButton b = toolButtons[0];
        Insets bi = b.getInsets();
        int toolTextWidth = Math.max(Math.round(b.getFont().getSize2D() * 7),
                tabWidth - in.left - in.right - UIScale.scale(DESC_GAP) - descMinimum
                        - toolColumn.getVerticalScrollBar().getPreferredSize().width - bi.left - bi.right
                        - UIScale.scale(2));
        for (int i = 0; i < toolButtons.length; i++) {
            toolButtons[i].setText(wrapped(toolButtons[i], Lang.get("TOOLS", TOOL_KEYS[i]), toolTextWidth,
                    true, false));
        }

        List<String> filterTexts = new ArrayList<>();
        filterTexts.add(hoverFilterText);
        for (ColorFilter f : FILTER_ORDER) {
            filterTexts.add(Lang.get("FILTER_DESC", f.name().replace("_", "")));
        }
        fitDescription(filterDesc, filterColumn, filterLabel, Lang.get("FILTER_DESC"), tabWidth, descMinimum,
                filterTexts);

        List<String> toolTexts = new ArrayList<>();
        toolTexts.add(hoverToolText);
        for (String key : TOOL_KEYS) {
            toolTexts.add(Lang.get("TOOL_DESC", key));
        }
        fitDescription(toolDesc, toolColumn, toolLabel, Lang.get("TOOL_DESC"), tabWidth, descMinimum, toolTexts);
    }

    private static void fitDescription(DescriptionArea desc, JComponent column, JLabel title, String titleText,
            int tabWidth, int minimumWidth, List<String> texts) {
        Insets in = UIScale.scale(TAB_INSETS);
        int width = Math.max(minimumWidth,
                tabWidth - in.left - in.right - column.getPreferredSize().width - UIScale.scale(DESC_GAP));
        title.setText(titleText);
        desc.fitTexts(width, texts);
    }

    /** Width taken by a check box besides its text. */
    private static int checkIconWidth(JCheckBox chk) {
        Icon icon = UIManager.getIcon("CheckBox.icon");
        Insets in = chk.getInsets();
        return (icon != null ? icon.getIconWidth() : UIScale.scale(16)) + chk.getIconTextGap() + in.left + in.right;
    }

    /**
     * The text itself when it fits on one line of {@code maxWidth} pixels in
     * the component's font, otherwise HTML with the words wrapped over
     * several lines ({@code \n} always breaks).
     */
    private static String wrapped(JComponent c, String text, int maxWidth, boolean center, boolean underline) {
        FontMetrics fm = c.getFontMetrics(c.getFont());
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() > 0 && fm.stringWidth(line + " " + word) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
            lines.add(line.toString());
        }
        if (lines.size() == 1) {
            return lines.get(0);
        }
        StringBuilder html = new StringBuilder("<html>");
        html.append(center ? "<center>" : "").append(underline ? "<u>" : "");
        for (int i = 0; i < lines.size(); i++) {
            html.append(i > 0 ? "<br>" : "").append(escapeHtml(lines.get(i)));
        }
        html.append(underline ? "</u>" : "").append(center ? "</center>" : "").append("</html>");
        return html.toString();
    }

    // ================================================================== helpers

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** "Filter / tool description": the button's text on hover. */
    private static void installHoverDescription(JButton button, JScrollPane column, DescriptionArea desc,
            Supplier<String> text, Supplier<String> hoverText) {
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                // WinForms sends no MouseEnter to a disabled button.
                if (button.isEnabled()) {
                    desc.setText(text.get());
                }
            }
        });
        installLeaveReset(button, column, desc, hoverText);
    }

    /** {@code filtersPanel.MouseLeave} / {@code tool_panel.MouseLeave}: back to the hover hint. */
    private static void installLeaveReset(JScrollPane column, DescriptionArea desc, Supplier<String> hoverText) {
        installLeaveReset(column, column, desc, hoverText);
        installLeaveReset(column.getViewport(), column, desc, hoverText);
        installLeaveReset((JComponent) column.getViewport().getView(), column, desc, hoverText);
        installLeaveReset(column.getVerticalScrollBar(), column, desc, hoverText);
    }

    private static void installLeaveReset(Component c, JScrollPane column, DescriptionArea desc,
            Supplier<String> hoverText) {
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), column);
                if (!column.contains(p)) {
                    desc.setText(hoverText.get());
                }
            }
        });
    }

    private static JLabel sectionLabel() {
        JLabel l = new JLabel();
        l.setFont(l.getFont().deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)));
        return l;
    }

    /** A bundled image that follows the user interface scale, like the fonts. */
    private static Icon scaledIcon(String name) {
        return new ScaledImageIcon(Resources.icon(name));
    }

    private static JButton iconButton(String image) {
        JButton b = new JButton(scaledIcon(image));
        b.setMargin(new Insets(2, 2, 2, 2));
        b.setPreferredSize(UIScale.scale(new Dimension(28, 26)));
        return b;
    }

    private static Border tabBorder() {
        return new EmptyBorder(UIScale.scale(TAB_INSETS));
    }

    private static JScrollPane scroller(JComponent view) {
        JScrollPane sp = new JScrollPane(view, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(UIScale.scale(16));
        return sp;
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    /** Start colour: a 6-digit hex code (with or without {@code #}) on the clipboard, else black. */
    private static Color initialColor() {
        String t = UiUtil.getClipboardText().trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() == 6) {
            Color c = ColorChanger.fromHtml(t);
            if (c != null) {
                return c;
            }
        }
        return Color.BLACK;
    }

    /** {@code Form1_FormClosing}: save the settings and end the program. */
    private void closeApplication() {
        settings.save();
        System.exit(0);
    }

    // ================================================================== small components

    /**
     * Text box with {@code MaxLength}, optional {@code CharacterCasing.Upper}
     * and the Tomato background of invalid input. A hex code box drops the
     * {@code #} and white space of typed or pasted text, so that a pasted
     * {@code #RRGGBB} fits in its six characters.
     */
    private static final class InputField extends JTextField {
        private static final long serialVersionUID = 1L;
        private final String sample;
        private boolean error;

        InputField(int maxLength, String sample, boolean upperCase, boolean hexCode) {
            super(sample.length());
            this.sample = sample;
            ((AbstractDocument) getDocument()).setDocumentFilter(new LimitFilter(maxLength, upperCase, hexCode));
            // TextBox.AllowDrop was False: dropped text is not accepted (pasting still works)
            setDropTarget(null);
        }

        boolean hasError() {
            return error;
        }

        /** Tomato background when {@code true}, the normal one otherwise. */
        void setError(boolean error) {
            this.error = error;
            setBackground(error ? UiUtil.TOMATO : UiUtil.windowBackground());
        }

        void setTextIfChanged(String text) {
            if (!text.equals(getText())) {
                setText(text);
            }
        }

        /** Wide enough for the sample text (e.g. "16777215"), not for {@code columns} "m" characters. */
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            if (!isPreferredSizeSet()) {
                Insets in = getInsets();
                d.width = getFontMetrics(getFont()).stringWidth(sample) + in.left + in.right + 4;
            }
            return d;
        }

        @Override
        public Dimension getMinimumSize() {
            return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
        }
    }

    /**
     * {@code MaxLength} (pasted text is truncated), optional upper-casing and,
     * for a hex code, removal of {@code #} and white space before the limit.
     * Package-private for the tests.
     */
    static final class LimitFilter extends DocumentFilter {
        private final int maxLength;
        private final boolean upperCase;
        private final boolean hexCode;

        LimitFilter(int maxLength, boolean upperCase, boolean hexCode) {
            this.maxLength = maxLength;
            this.upperCase = upperCase;
            this.hexCode = hexCode;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null || text.isEmpty()) {
                fb.replace(offset, length, text, attrs);
                return;
            }
            String s = hexCode ? text.replaceAll("[#\\s\\p{Z}]", "") : text;
            if (s.isEmpty()) {
                // only "#" or white space: keep the document (and any selection) as it is
                return;
            }
            s = upperCase ? s.toUpperCase(Locale.ROOT) : s;
            int room = maxLength - (fb.getDocument().getLength() - length);
            if (room <= 0) {
                if (length > 0) {
                    fb.remove(offset, length);
                }
                return;
            }
            fb.replace(offset, length, s.length() > room ? s.substring(0, room) : s, attrs);
        }
    }

    /**
     * Read-only wrapping text ({@code filterDesc} / {@code toolDesc}). Its size
     * is fixed to fit the longest of a set of texts, so hovering never changes
     * the layout.
     */
    private static final class DescriptionArea extends JTextArea {
        private static final long serialVersionUID = 1L;
        private Dimension fixedSize = new Dimension(100, 40);

        DescriptionArea() {
            setEditable(false);
            setFocusable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(null);
            setHighlighter(null);
            setFont(UIManager.getFont("Label.font"));
            setForeground(uiColor("Label.foreground", Color.BLACK));
            setCursor(Cursor.getDefaultCursor());
        }

        void fitTexts(int width, List<String> texts) {
            JTextArea probe = new JTextArea();
            probe.setLineWrap(true);
            probe.setWrapStyleWord(true);
            probe.setBorder(null);
            probe.setFont(getFont());
            int height = 0;
            for (String t : texts) {
                probe.setText(t);
                probe.setSize(width, Short.MAX_VALUE);
                height = Math.max(height, probe.getPreferredSize().height);
            }
            fixedSize = new Dimension(width, height);
            revalidate();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(fixedSize);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(fixedSize);
        }
    }

    /**
     * Content of an auto-scrolling panel: follows the viewport width, scrolls
     * vertically, and asks for a small viewport height so that it never makes
     * the tabs taller.
     */
    private static final class ScrollablePanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        ScrollablePanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            Dimension d = getPreferredSize();
            return new Dimension(d.width, Math.min(d.height, UIScale.scale(SCROLL_VIEWPORT_HEIGHT)));
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return UIScale.scale(16);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            int unit = UIScale.scale(16);
            return orientation == SwingConstants.VERTICAL ? Math.max(unit, visible.height - unit) : visible.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
