package colorpicker.ui.tools;

import colorpicker.App;
import colorpicker.core.ColorChanger;
import colorpicker.core.ImageColorExtractor;
import colorpicker.core.RGBColor;
import colorpicker.core.VB;
import colorpicker.i18n.Lang;
import colorpicker.settings.AppSettings;
import colorpicker.ui.Resources;
import colorpicker.ui.UiUtil;
import com.formdev.flatlaf.util.UIScale;
import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

/**
 * "Get all colors in an image" tool (port of {@code GetImageColorForm}).
 *
 * <p>The pixel work is done by {@link ImageColorExtractor} on a
 * {@link SwingWorker}; the list is a {@link JTable} over an int-array model so
 * that images with hundreds of thousands of colours stay responsive. The
 * {@code BackgroundWorker2} progress loop is a Swing {@link Timer}. The search
 * box, broken in the original, filters the list.
 */
public final class ImageColorsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Rows handed to the table per {@code publish}. */
    private static final int BATCH = 2048;
    /** Visible rows of the list (the original list was 130 px high). */
    private static final int VISIBLE_ROWS = 6;

    private static final DataFlavor URI_LIST_FLAVOR = uriListFlavor();

    private static ImageColorsDialog instance;

    private enum State { IDLE, CHECKING, RUNNING, STOPPING }

    private enum Stage { VALID, INITIALIZING, GATHERING, POPULATING }

    /** A progress message from the worker; {@code colors} is set once, when populating starts. */
    private record Progress(Stage stage, int value, int[] colors) {
    }

    private final JLabel pathLabel = new JLabel();
    private final JTextField pathField = new JTextField(24);
    private final JButton browseButton = new JButton("...");
    private final JLabel statusLabel = new StatusLabel();
    private final JLabel progressLabel = new StatusLabel();
    private final JButton startButton = new JButton();
    private final JProgressBar progressBar = new JProgressBar();
    private final JTextField searchField = new JTextField();
    private final ColorTableModel model = new ColorTableModel();
    private final JTable table = new JTable(model);
    private final JButton selectButton = new JButton();
    private final JButton closeButton = new JButton();
    private final Timer progressTimer = new Timer(200, e -> updatePopulatingProgress());
    private final Runnable langListener = this::updateLang;

    private State state = State.IDLE;
    private Supplier<String> statusText = () -> Lang.get("TOOL_ALLCOLORS", "MSG_IDLE");
    private Supplier<String> progressText = () -> "";
    private ExtractWorker worker;
    private long populateStart;
    private boolean placeholderShown;
    private boolean updatingSearch;
    private boolean disposed;

    private ImageColorsDialog(Window owner) {
        super(owner, ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setIconImages(Resources.appIcons());
        setResizable(false);
        buildUi();
        wireEvents();
        showPlaceholder();
        updateLang();
        Lang.addListener(langListener);
    }

    /** Shows the tool window (reusing the open one), like VB's Form.Show() + BringToFront(). */
    public static void showDialog(Window owner) {
        if (instance == null || instance.disposed) {
            instance = new ImageColorsDialog(owner);
            instance.setLocationRelativeTo(owner);
        }
        instance.setVisible(true);
        instance.toFront();
    }

    // ------------------------------------------------------------------ layout

    // Fixed pixel lengths are at 100 % and go through UIScale.scale (large desktop fonts).
    private void buildUi() {
        JPanel content = new JPanel(new GridBagLayout());
        setContentPane(content);

        // Button margins and JComponent.minimumWidth are scaled by FlatLaf itself.
        browseButton.setMargin(new Insets(1, 6, 1, 6));
        browseButton.putClientProperty("JComponent.minimumWidth", 0);
        JPanel pathRow = new JPanel(new BorderLayout(UIScale.scale(6), 0));
        pathRow.setOpaque(false);
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browseButton, BorderLayout.LINE_END);

        JPanel labels = new JPanel(new GridLayout(2, 1, 0, UIScale.scale(4)));
        labels.setOpaque(false);
        labels.add(statusLabel);
        labels.add(progressLabel);
        JPanel statusRow = new JPanel(new BorderLayout(UIScale.scale(6), 0));
        statusRow.setOpaque(false);
        statusRow.add(labels, BorderLayout.CENTER);
        statusRow.add(startButton, BorderLayout.LINE_END);

        progressBar.setPreferredSize(UIScale.scale(new Dimension(10, 12)));

        // Hairline: 1 px at every scale (as in ColorCombinerDialog).
        JPanel separator = new JPanel();
        separator.setBackground(Color.GRAY);
        separator.setPreferredSize(new Dimension(1, 1));
        separator.setMinimumSize(new Dimension(1, 1));

        configureTable();
        JScrollPane tableScroll = new JScrollPane(table);

        selectButton.setFont(selectButton.getFont().deriveFont(Font.BOLD));
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.LINE_AXIS));
        buttons.add(Box.createHorizontalGlue());
        buttons.add(closeButton);
        buttons.add(Box.createHorizontalStrut(UIScale.scale(6)));
        buttons.add(selectButton);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_START;
        int y = 0;
        add(content, pathLabel, c, y++, UIScale.scale(new Insets(9, 12, 3, 12)));
        add(content, pathRow, c, y++, UIScale.scale(new Insets(0, 12, 6, 12)));
        add(content, statusRow, c, y++, UIScale.scale(new Insets(0, 12, 6, 12)));
        add(content, progressBar, c, y++, UIScale.scale(new Insets(0, 12, 6, 12)));
        add(content, separator, c, y++, UIScale.scale(new Insets(0, 0, 6, 0)));
        add(content, searchField, c, y++, UIScale.scale(new Insets(0, 12, 6, 12)));
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        add(content, tableScroll, c, y++, UIScale.scale(new Insets(0, 12, 6, 12)));
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weighty = 0;
        add(content, buttons, c, y, UIScale.scale(new Insets(0, 12, 6, 12)));

        // TabIndex order of the designer (the search box was hidden there).
        setFocusTraversalPolicy(new OrderedFocusPolicy(List.of(
                pathField, browseButton, startButton, searchField, table, selectButton, closeButton)));

        startButton.setEnabled(false);
        selectButton.setEnabled(false);
    }

    private static void add(Container parent, Component comp, GridBagConstraints c, int row, Insets insets) {
        c.gridy = row;
        c.insets = insets;
        parent.add(comp, c);
    }

    private void configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        if (table.getTableHeader().getDefaultRenderer() instanceof JLabel header) {
            header.setHorizontalAlignment(SwingConstants.LEADING);
        }
        table.setDefaultRenderer(Object.class, new ColorCellRenderer());

        // Like a ListView, Tab leaves the list instead of moving between cells.
        table.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Set.<AWTKeyStroke>of(
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK)));
        table.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, Set.<AWTKeyStroke>of(
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)));

        // Designer widths (199 / 60 / 60 / 60 at 8.25 pt), scaled to the current font,
        // never below the designer widths at the current UI scale.
        float scale = Math.max(UIScale.getUserScaleFactor(), table.getFont().getSize2D() / 11f);
        int[] widths = {199, 60, 60, 60};
        TableColumnModel columns = table.getColumnModel();
        int total = 0;
        for (int i = 0; i < widths.length; i++) {
            int w = Math.round(widths[i] * scale);
            columns.getColumn(i).setPreferredWidth(w);
            total += w;
        }
        table.setPreferredScrollableViewportSize(new Dimension(total, table.getRowHeight() * VISIBLE_ROWS));
    }

    private void wireEvents() {
        pathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                pathChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                pathChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // attribute changes only
            }
        });
        browseButton.addActionListener(e -> browse());
        startButton.addActionListener(e -> startOrStop());
        selectButton.addActionListener(e -> selectColor());
        closeButton.addActionListener(e -> dispose());
        table.getSelectionModel().addListSelectionListener(
                e -> selectButton.setEnabled(table.getSelectedRowCount() == 1));

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // attribute changes only
            }
        });
        searchField.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                hidePlaceholder();
            }
        });
        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                hidePlaceholder();
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (searchField.getText().isEmpty()) {
                    showPlaceholder();
                }
            }
        });

        // Linux addition: drop an image file anywhere on the window; text goes to the two boxes.
        new DropTarget(getContentPane(), DnDConstants.ACTION_COPY, new FileDropListener(null), true);
        new DropTarget(table, DnDConstants.ACTION_COPY, new FileDropListener(null), true);
        new DropTarget(pathField, DnDConstants.ACTION_COPY, new FileDropListener(pathField), true);
        new DropTarget(searchField, DnDConstants.ACTION_COPY, new FileDropListener(searchField), true);
    }

    // ---------------------------------------------------------------- language

    /** {@code updateLang()}: refreshes every text, then resizes the window to fit them. */
    private void updateLang() {
        setTitle(Lang.get("TOOL_ALLCOLORS"));
        pathLabel.setText(Lang.get("TOOL_ALLCOLORS", "PATH"));
        selectButton.setText(Lang.get("TOOL_ALLCOLORS", "SELECT"));
        closeButton.setText(Lang.get("COMMON", "CLOSE"));
        String[] headers = {"#", Lang.get("RGB", "R"), Lang.get("RGB", "G"), Lang.get("RGB", "B")};
        for (int i = 0; i < headers.length; i++) {
            table.getColumnModel().getColumn(i).setHeaderValue(headers[i]);
        }
        table.getTableHeader().repaint();
        if (placeholderShown) {
            showPlaceholder();
        }
        updateStartButton();

        sizeButton(closeButton, 81, 24);
        sizeButton(selectButton, 118, 24);
        sizeStartButton();
        sizeStatusLabels();
        refreshStatus();
        refreshProgressText();
        pack();
    }

    /** Minimum size of a designer button (at 100 %, scaled here), growing with its text. */
    private static void sizeButton(JButton button, int minWidth, int minHeight) {
        button.setPreferredSize(null);
        Dimension d = button.getPreferredSize();
        button.setPreferredSize(new Dimension(Math.max(d.width, UIScale.scale(minWidth)),
                Math.max(d.height, UIScale.scale(minHeight))));
    }

    /** The Start button is wide enough for Start, Stop and Stopping... so it never jumps. */
    private void sizeStartButton() {
        String current = startButton.getText();
        startButton.setPreferredSize(null);
        int width = UIScale.scale(104);
        for (String text : new String[] {Lang.get("COMMON", "START"), Lang.get("COMMON", "STOP"),
                Lang.get("TOOL_ALLCOLORS", "MSG_STOPPING")}) {
            startButton.setText(text);
            width = Math.max(width, startButton.getPreferredSize().width);
        }
        startButton.setText(current);
        startButton.setPreferredSize(
                new Dimension(width, Math.max(UIScale.scale(32), startButton.getPreferredSize().height)));
    }

    /**
     * The status labels are sized for the longest messages of the current
     * language (largest image and colour counts); anything longer is cut with
     * an ellipsis and shown in full as a tooltip.
     */
    private void sizeStatusLabels() {
        FontMetrics fm = statusLabel.getFontMetrics(statusLabel.getFont());
        int maxColors = ImageColorExtractor.COLOR_SPACE_SIZE;
        String[] samples = {
            doneText(ImageColorExtractor.maxPixels(), maxColors),
            populatingText(maxColors - maxColors / 9, maxColors),
            Lang.get("TOOL_ALLCOLORS", "MSG_ETA").replace("{0}", timeText(59)),
            elapsedText(59),
            Lang.get("TOOL_ALLCOLORS", "MSG_CHECKING"),
            Lang.get("TOOL_ALLCOLORS", "MSG_INITIALIZING"),
            Lang.get("TOOL_ALLCOLORS", "MSG_GATHERING"),
            Lang.get("TOOL_ALLCOLORS", "MSG_STOPPING"),
        };
        int width = UIScale.scale(313);
        int padding = UIScale.scale(4);
        for (String s : samples) {
            width = Math.max(width, fm.stringWidth(s) + padding);
        }
        Dimension d = new Dimension(width, fm.getHeight());
        statusLabel.setPreferredSize(d);
        progressLabel.setPreferredSize(d);
    }

    // ------------------------------------------------------------ path / browse

    /** {@code TextBox1_TextChanged}: red box and disabled Start when the file does not exist. */
    private void pathChanged() {
        boolean exists = fileExists(pathField.getText());
        pathField.setBackground(exists ? UiUtil.windowBackground() : UiUtil.TOMATO);
        if (state == State.IDLE) {
            startButton.setEnabled(exists);
        }
    }

    private static boolean fileExists(String text) {
        Path p = toPath(text);
        return p != null && Files.isRegularFile(p);
    }

    /** The typed path; {@code file:} URIs (pasted from a file manager) are accepted too. */
    static Path toPath(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            if (text.startsWith("file:")) {
                return Paths.get(new URI(text.trim()));
            }
            return Paths.get(text);
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
            return null;
        }
    }

    /** {@code Button1_Click}: open file dialog starting in the Documents folder. */
    private void browse() {
        JFileChooser chooser = new JFileChooser(documentsDirectory());
        chooser.setDialogTitle(Lang.get("TOOL_ALLCOLORS", "MSG_CHOOSE"));
        chooser.setAcceptAllFileFilterUsed(false);
        FileFilter all = new FileFilter() {
            @Override
            public boolean accept(File f) {
                return true;
            }

            @Override
            public String getDescription() {
                return Lang.get("TOOL_ALLCOLORS", "MSG_DIALOGFILTER");
            }
        };
        List<String> suffixes = ImageColorExtractor.supportedSuffixes();
        FileFilter images = null;
        if (!suffixes.isEmpty()) {
            StringBuilder description = new StringBuilder();
            for (String s : suffixes) {
                description.append(description.length() == 0 ? "" : "; ").append("*.").append(s);
            }
            images = new FileNameExtensionFilter(description.toString(), suffixes.toArray(new String[0]));
            chooser.addChoosableFileFilter(images);
        }
        chooser.addChoosableFileFilter(all);
        chooser.setFileFilter(images != null ? images : all);
        Path current = toPath(pathField.getText());
        if (current != null && Files.isRegularFile(current)) {
            chooser.setSelectedFile(current.toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            setPath(chooser.getSelectedFile().getPath());
        }
    }

    /** {@code updateStuff}. */
    private void setPath(String path) {
        pathField.setText(path);
    }

    /**
     * {@code My.Computer.FileSystem.SpecialDirectories.MyDocuments}: the XDG
     * documents directory ({@code ~/.config/user-dirs.dirs}), else the home.
     */
    static File documentsDirectory() {
        Path home = Paths.get(System.getProperty("user.home"));
        String xdgConfig = System.getenv("XDG_CONFIG_HOME");
        Path config = xdgConfig != null && !xdgConfig.isBlank() ? Paths.get(xdgConfig) : home.resolve(".config");
        try {
            for (String line : Files.readAllLines(config.resolve("user-dirs.dirs"))) {
                String l = line.trim();
                if (!l.startsWith("XDG_DOCUMENTS_DIR=")) {
                    continue;
                }
                String v = l.substring("XDG_DOCUMENTS_DIR=".length()).trim();
                if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1);
                }
                v = v.replace("$HOME", home.toString());
                Path p = Paths.get(v);
                if (p.isAbsolute() && Files.isDirectory(p)) {
                    return p.toFile();
                }
            }
        } catch (IOException | RuntimeException e) {
            // no user-dirs.dirs: use the fallback below
        }
        Path documents = home.resolve("Documents");
        return (Files.isDirectory(documents) ? documents : home).toFile();
    }

    // ------------------------------------------------------------- start / stop

    /** {@code Button2_Click}: Start, or Stop while running. */
    private void startOrStop() {
        if (state == State.IDLE) {
            start();
        } else if (state == State.RUNNING) {
            stop();
        }
    }

    private void start() {
        setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_CHECKING"));
        state = State.CHECKING;
        updateStartButton();
        worker = new ExtractWorker(toPath(pathField.getText()), AppSettings.get().isOptimizeSpeed());
        worker.execute();
    }

    private void stop() {
        if (worker == null) {
            return;
        }
        state = State.STOPPING;
        worker.cancelled.set(true);
        setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_STOPPING"));
        updateStartButton();
    }

    private void updateStartButton() {
        switch (state) {
            case IDLE -> {
                startButton.setText(Lang.get("COMMON", "START"));
                startButton.setEnabled(fileExists(pathField.getText()));
            }
            case CHECKING -> {
                startButton.setText(Lang.get("COMMON", "START"));
                startButton.setEnabled(false);
            }
            case RUNNING -> {
                startButton.setText(Lang.get("COMMON", "STOP"));
                startButton.setEnabled(true);
            }
            case STOPPING -> {
                startButton.setText(Lang.get("TOOL_ALLCOLORS", "MSG_STOPPING"));
                startButton.setEnabled(false);
            }
        }
    }

    /** The image passed the checks: clear the list and turn Start into Stop. */
    private void validated() {
        state = State.RUNNING;
        updateStartButton();
        model.reset(new int[0]);
        progressBar.setMaximum(100);
        progressBar.setValue(0);
        setProgressText(() -> "");
        updateSearchBackground(false);
    }

    private void startPopulating(int[] colors) {
        model.reset(colors);
        progressBar.setMaximum(Math.max(colors.length, 1));
        progressBar.setValue(0);
        setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_POPULATING"));
        populateStart = System.nanoTime();
        progressTimer.setDelay(AppSettings.get().isOptimizeSpeed() ? 400 : 200);
        progressTimer.restart();
    }

    /** {@code BackgroundWorker2} loop body: progress bar, percentage and estimated time. */
    private void updatePopulatingProgress() {
        if (state != State.RUNNING) {
            return;
        }
        int value = model.available();
        int max = model.total();
        progressBar.setValue(value);
        double seconds = (System.nanoTime() - populateStart) / 1e9;
        int speed = seconds > 0 ? VB.cint(value / seconds) : 0;
        if (speed > 0) {
            long eta = (long) VB.round((max - value) / (double) speed);
            setProgressText(() -> Lang.get("TOOL_ALLCOLORS", "MSG_ETA").replace("{0}", timeText(eta)));
        }
        setStatus(() -> populatingText(value, max));
    }

    /** {@code BackgroundWorker1_RunWorkerCompleted} equivalent. */
    private void finished(ExtractWorker w) {
        progressTimer.stop();
        if (worker == w) {
            worker = null;
        }
        if (disposed) {
            return;
        }
        long elapsed = w.startNanos == 0 ? 0 : (System.nanoTime() - w.startNanos) / 1_000_000_000L;
        String error = null;
        try {
            ImageColorExtractor.Result result = w.get();
            if (result == null) {
                setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_IDLE"));
                error = Lang.get("TOOL_ALLCOLORS", "MSG_INVALID");
            } else {
                // done() may run before the last process() chunks (SwingWorker coalesces them).
                if (!model.holds(result.colors())) {
                    model.reset(result.colors());
                }
                model.setAvailable(result.distinctCount());
                progressBar.setMaximum(Math.max(result.distinctCount(), 1));
                progressBar.setValue(result.distinctCount());
                long total = result.totalPixels();
                int count = result.distinctCount();
                setStatus(() -> doneText(total, count));
                setProgressText(() -> elapsedText(elapsed));
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CancellationException) {
                setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_IDLE"));
                setProgressText(() -> elapsedText(elapsed));
            } else if (cause instanceof OutOfMemoryError
                    || cause instanceof ImageColorExtractor.ImageTooBigException) {
                model.reset(new int[0]);
                setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_IDLE"));
                setProgressText(() -> elapsedText(0));
                error = Lang.get("TOOL_ALLCOLORS", "MSG_IMGTOOBIG");
            } else {
                model.reset(new int[0]);
                setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_IDLE"));
                error = Lang.get("TOOL_ALLCOLORS", "MSG_INVALID");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (CancellationException e) {
            setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_IDLE"));
        }
        state = State.IDLE;
        updateStartButton();
        updateSearchBackground(false);
        if (error != null) {
            toFront();
            JOptionPane.showMessageDialog(this, error, Lang.get("COMMON", "ERROR"), JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ texts

    private void setStatus(Supplier<String> text) {
        statusText = text;
        refreshStatus();
    }

    private void refreshStatus() {
        statusLabel.setText(statusText.get());
    }

    private void setProgressText(Supplier<String> text) {
        progressText = text;
        refreshProgressText();
    }

    private void refreshProgressText() {
        progressLabel.setText(progressText.get());
    }

    private static String doneText(long total, int count) {
        return Lang.get("TOOL_ALLCOLORS", "MSG_DONE")
                .replace("{0}", Long.toString(total))
                .replace("{1}", Integer.toString(count));
    }

    private static String populatingText(int value, int max) {
        double percent = max == 0 ? 100 : value / (double) max * 100;
        return Lang.get("TOOL_ALLCOLORS", "MSG_POPULATING") + " " + formatPercent(percent)
                + "% (" + value + " / " + max + ")";
    }

    private static String elapsedText(long seconds) {
        return Lang.get("TOOL_ALLCOLORS", "MSG_ELAPSED") + ": " + timeText(seconds);
    }

    /** {@code Math.Round(x, 2)} printed with the system number format. */
    static String formatPercent(double percent) {
        DecimalFormat format = new DecimalFormat("0.##",
                DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT)));
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        return format.format(percent);
    }

    /** {@code SecondsToDate}: "{0} seconds" under a minute, else H:mm:ss. */
    static String timeText(long seconds) {
        if (seconds < 60) {
            return Lang.get("TOOL_ALLCOLORS", "MSG_SECS").replace("{0}", Long.toString(seconds));
        }
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    /** {@code grayscale()}: text is black on light rows and white on dark ones. */
    static int luma(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return VB.cint(r * 0.3 + g * 0.59 + b * 0.11);
    }

    // ----------------------------------------------------------------- select

    /** {@code Button3_Click}: the selected colour becomes the current colour. */
    private void selectColor() {
        int row = table.getSelectedRow();
        if (table.getSelectedRowCount() != 1 || row < 0) {
            return;
        }
        int rgb = model.colorAt(row);
        App.colorManager().setRGB(new RGBColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
    }

    // ----------------------------------------------------------------- search

    private void showPlaceholder() {
        placeholderShown = true;
        setSearchText(Lang.get("COMMON", "SEARCH") + "...");
        searchField.setForeground(UiUtil.DARK_GRAY);
    }

    /** {@code TextBox2_Click}: the grey hint disappears when the user enters the box. */
    private void hidePlaceholder() {
        if (!placeholderShown) {
            return;
        }
        placeholderShown = false;
        setSearchText("");
        searchField.setForeground(UiUtil.controlText());
    }

    private void setSearchText(String text) {
        updatingSearch = true;
        try {
            searchField.setText(text);
        } finally {
            updatingSearch = false;
        }
    }

    /** {@code TextBox2_TextChanged}: filters the list; red box and a beep when nothing matches. */
    private void searchChanged() {
        if (updatingSearch || placeholderShown) {
            return;
        }
        String text = searchField.getText();
        int selected = table.getSelectedRow() >= 0 ? model.indexAt(table.getSelectedRow()) : -1;
        model.setQuery(SearchQuery.parse(text));
        int row = selected >= 0 ? model.rowOf(selected) : -1;
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
        updateSearchBackground(true);
    }

    private void updateSearchBackground(boolean beep) {
        boolean miss = !placeholderShown && !searchField.getText().isEmpty() && model.getRowCount() == 0;
        searchField.setBackground(miss ? UiUtil.TOMATO : UiUtil.windowBackground());
        if (miss && beep) {
            UiUtil.beep();
        }
    }

    // ---------------------------------------------------------------- dispose

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            Lang.removeListener(langListener);
            progressTimer.stop();
            if (worker != null) {
                worker.cancelled.set(true);
            }
            if (instance == this) {
                instance = null;
            }
        }
        super.dispose();
    }

    // ----------------------------------------------------------------- worker

    /** {@code BackgroundWorker1}: checks the file, extracts the colours, feeds the list. */
    private final class ExtractWorker extends SwingWorker<ImageColorExtractor.Result, Progress> {
        private final Path file;
        private final boolean optimizeSpeed;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile long startNanos;

        ExtractWorker(Path file, boolean optimizeSpeed) {
            this.file = file;
            this.optimizeSpeed = optimizeSpeed;
        }

        @Override
        protected ImageColorExtractor.Result doInBackground() throws Exception {
            if (file == null || !ImageColorExtractor.canExtract(file)) {
                return null;
            }
            startNanos = System.nanoTime();
            publish(new Progress(Stage.VALID, 0, null));
            publish(new Progress(Stage.INITIALIZING, 0, null));
            ImageColorExtractor.Result result = ImageColorExtractor.extract(file, cancelled::get,
                    (phase, percent) -> publish(new Progress(
                            phase == ImageColorExtractor.Phase.INITIALIZING ? Stage.INITIALIZING : Stage.GATHERING,
                            percent, null)));
            int[] colors = result.colors();
            publish(new Progress(Stage.POPULATING, 0, colors));
            int done = 0;
            while (done < colors.length) {
                if (cancelled.get()) {
                    throw new CancellationException();
                }
                done = Math.min(colors.length, done + BATCH);
                publish(new Progress(Stage.POPULATING, done, null));
                if (optimizeSpeed) {
                    // The original slept 1 ms per item in "optimize speed" mode.
                    Thread.sleep(1);
                }
            }
            return result;
        }

        @Override
        protected void process(List<Progress> chunks) {
            if (disposed || worker != this || state == State.STOPPING) {
                return;
            }
            int populated = -1;
            for (Progress p : chunks) {
                switch (p.stage()) {
                    case VALID -> validated();
                    case INITIALIZING -> {
                        setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_INITIALIZING"));
                        progressBar.setValue(p.value());
                    }
                    case GATHERING -> {
                        setStatus(() -> Lang.get("TOOL_ALLCOLORS", "MSG_GATHERING"));
                        progressBar.setValue(p.value());
                    }
                    case POPULATING -> {
                        if (p.colors() != null) {
                            startPopulating(p.colors());
                        } else {
                            populated = Math.max(populated, p.value());
                        }
                    }
                }
            }
            if (populated >= 0) {
                model.setAvailable(populated);
                if (!placeholderShown && !searchField.getText().isEmpty()) {
                    updateSearchBackground(false);
                }
            }
        }

        @Override
        protected void done() {
            finished(this);
        }
    }

    // ------------------------------------------------------------------ model

    /**
     * The colour list: an int array of {@code 0xRRGGBB} values of which the
     * first {@code available} rows are populated, optionally filtered by a
     * {@link SearchQuery} through an index array (4 bytes per visible row).
     */
    static final class ColorTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final int[] EMPTY = new int[0];

        private int[] colors = EMPTY;
        private int available;
        private transient SearchQuery query;
        private int[] view = EMPTY;
        private int viewCount;

        @Override
        public int getRowCount() {
            return query == null ? available : viewCount;
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "#";
                case 1 -> "R";
                case 2 -> "G";
                default -> "B";
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            int rgb = colorAt(row);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return switch (column) {
                case 0 -> ColorChanger.toHtml(r, g, b);
                case 1 -> r;
                case 2 -> g;
                default -> b;
            };
        }

        int colorAt(int row) {
            return colors[indexAt(row)];
        }

        /** Index in the colour array of a visible row. */
        int indexAt(int row) {
            return query == null ? row : view[row];
        }

        /** Visible row of a colour index, or -1 when filtered out. */
        int rowOf(int index) {
            if (query == null) {
                return index < available ? index : -1;
            }
            int row = Arrays.binarySearch(view, 0, viewCount, index);
            return row >= 0 ? row : -1;
        }

        int available() {
            return available;
        }

        int total() {
            return colors.length;
        }

        boolean holds(int[] array) {
            return colors == array;
        }

        /** New colour array with no row populated yet. */
        void reset(int[] newColors) {
            colors = newColors;
            available = 0;
            viewCount = 0;
            fireTableDataChanged();
        }

        /** Makes the first {@code count} colours visible (filtered when searching). */
        void setAvailable(int count) {
            int n = Math.min(count, colors.length);
            if (n <= available) {
                return;
            }
            int before = getRowCount();
            if (query != null) {
                appendMatches(available, n);
            }
            available = n;
            int after = getRowCount();
            if (after > before) {
                fireTableRowsInserted(before, after - 1);
            }
        }

        /** {@code null} shows every row. */
        void setQuery(SearchQuery q) {
            query = q;
            viewCount = 0;
            if (q == null) {
                view = EMPTY;
            } else {
                appendMatches(0, available);
            }
            fireTableDataChanged();
        }

        private void appendMatches(int from, int to) {
            for (int i = from; i < to; i++) {
                if (query.matches(colors[i])) {
                    if (viewCount == view.length) {
                        view = Arrays.copyOf(view, Math.max(1024, viewCount * 2));
                    }
                    view[viewCount++] = i;
                }
            }
        }
    }

    /**
     * A search typed in the search box: a row matches when one of its columns
     * starts with the text, ignoring case and a leading {@code #}. Works on the
     * int value, no strings are built.
     */
    static final class SearchQuery {
        /** Number of hex digits to compare, or -1 when the text is not a hex prefix. */
        private final int hexDigits;
        private final int hexValue;
        /** Channel values (0-255) whose decimal text starts with the query, or null. */
        private final boolean[] decimal;

        private SearchQuery(int hexDigits, int hexValue, boolean[] decimal) {
            this.hexDigits = hexDigits;
            this.hexValue = hexValue;
            this.decimal = decimal;
        }

        /** @return {@code null} when every row matches (empty text or a lone {@code #}) */
        static SearchQuery parse(String text) {
            String t = text == null ? "" : text.trim();
            boolean hash = t.startsWith("#");
            String q = hash ? t.substring(1) : t;
            if (q.isEmpty()) {
                return null;
            }
            int hexDigits = -1;
            int hexValue = 0;
            if (q.length() <= 6 && q.chars().allMatch(ch -> Character.digit(ch, 16) >= 0)) {
                hexDigits = q.length();
                hexValue = Integer.parseInt(q, 16);
            }
            boolean[] decimal = null;
            if (!hash && q.length() <= 3 && q.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
                decimal = new boolean[256];
                for (int v = 0; v < 256; v++) {
                    decimal[v] = Integer.toString(v).startsWith(q);
                }
            }
            return new SearchQuery(hexDigits, hexValue, decimal);
        }

        boolean matches(int rgb) {
            int c = rgb & 0xFFFFFF;
            if (hexDigits > 0 && (c >>> (24 - 4 * hexDigits)) == hexValue) {
                return true;
            }
            return decimal != null
                    && (decimal[(c >> 16) & 0xFF] || decimal[(c >> 8) & 0xFF] || decimal[c & 0xFF]);
        }
    }

    /** Row background is the colour, text black or white for contrast. */
    private static final class ColorCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private final EmptyBorder padding = new EmptyBorder(UIScale.scale(new Insets(0, 4, 0, 4)));

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            if (!isSelected && table.getModel() instanceof ColorTableModel m && row < m.getRowCount()) {
                int rgb = m.colorAt(row);
                setBackground(new Color(rgb));
                setForeground(luma(rgb) > 126 ? Color.BLACK : Color.WHITE);
            }
            setBorder(padding);
            return this;
        }
    }

    /** A status line whose full text is shown as a tooltip only while it is cut. */
    private static final class StatusLabel extends JLabel {
        private static final long serialVersionUID = 1L;

        StatusLabel() {
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override
        public String getToolTipText() {
            String text = getText();
            if (text == null || text.isEmpty()) {
                return null;
            }
            Insets insets = getInsets();
            int available = getWidth() - insets.left - insets.right;
            return getFontMetrics(getFont()).stringWidth(text) > available ? text : null;
        }
    }

    // --------------------------------------------------------------- focus/dnd

    /** Focus order of the designer's TabIndex values. */
    private static final class OrderedFocusPolicy extends FocusTraversalPolicy {
        private final List<Component> order;

        OrderedFocusPolicy(List<Component> order) {
            this.order = order;
        }

        private static boolean accepts(Component c) {
            return c.isShowing() && c.isEnabled() && c.isFocusable();
        }

        private Component step(Component from, int direction) {
            int n = order.size();
            int start = from == null ? -1 : order.indexOf(from);
            if (start < 0) {
                start = direction > 0 ? -1 : 0;
            }
            for (int k = 1; k <= n; k++) {
                Component c = order.get(Math.floorMod(start + k * direction, n));
                if (accepts(c)) {
                    return c;
                }
            }
            return null;
        }

        @Override
        public Component getComponentAfter(Container root, Component c) {
            return step(c, 1);
        }

        @Override
        public Component getComponentBefore(Container root, Component c) {
            return step(c, -1);
        }

        @Override
        public Component getFirstComponent(Container root) {
            return step(null, 1);
        }

        @Override
        public Component getLastComponent(Container root) {
            return step(null, -1);
        }

        @Override
        public Component getDefaultComponent(Container root) {
            return getFirstComponent(root);
        }
    }

    /** Accepts a dropped file (and, on the path and search boxes, plain text). */
    private final class FileDropListener extends DropTargetAdapter {
        /** Box that receives dropped text, or {@code null} to refuse text. */
        private final JTextField textTarget;

        FileDropListener(JTextField textTarget) {
            this.textTarget = textTarget;
        }

        @Override
        public void dragEnter(DropTargetDragEvent e) {
            check(e);
        }

        @Override
        public void dragOver(DropTargetDragEvent e) {
            check(e);
        }

        @Override
        public void dropActionChanged(DropTargetDragEvent e) {
            check(e);
        }

        private void check(DropTargetDragEvent e) {
            if (hasFiles(e.getCurrentDataFlavors())
                    || (textTarget != null && e.isDataFlavorSupported(DataFlavor.stringFlavor))) {
                e.acceptDrag(DnDConstants.ACTION_COPY);
            } else {
                e.rejectDrag();
            }
        }

        @Override
        public void drop(DropTargetDropEvent e) {
            e.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable t = e.getTransferable();
            Path file = droppedFile(t);
            if (file != null) {
                setPath(file.toString());
                e.dropComplete(true);
                return;
            }
            String text = textTarget != null ? droppedText(t) : null;
            if (text != null) {
                Path p = text.startsWith("file:") ? toPath(text.lines().findFirst().orElse("")) : null;
                if (p != null) {
                    setPath(p.toString());
                } else {
                    if (textTarget == searchField) {
                        hidePlaceholder();
                    }
                    textTarget.replaceSelection(text);
                    textTarget.requestFocusInWindow();
                }
            }
            e.dropComplete(text != null);
        }
    }

    private static boolean hasFiles(DataFlavor[] flavors) {
        for (DataFlavor f : flavors) {
            if (f.isFlavorJavaFileListType() || (URI_LIST_FLAVOR != null && f.equals(URI_LIST_FLAVOR))) {
                return true;
            }
        }
        return false;
    }

    private static Path droppedFile(Transferable t) {
        try {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    && t.getTransferData(DataFlavor.javaFileListFlavor) instanceof List<?> files) {
                for (Object o : files) {
                    if (o instanceof File f) {
                        return f.toPath();
                    }
                }
            }
            if (URI_LIST_FLAVOR != null && t.isDataFlavorSupported(URI_LIST_FLAVOR)
                    && t.getTransferData(URI_LIST_FLAVOR) instanceof String uris) {
                for (String line : uris.split("\r?\n")) {
                    String l = line.trim();
                    if (l.startsWith("file:")) {
                        Path p = toPath(l);
                        if (p != null) {
                            return p;
                        }
                    }
                }
            }
        } catch (UnsupportedFlavorException | IOException | RuntimeException e) {
            // not a file drop
        }
        return null;
    }

    private static String droppedText(Transferable t) {
        try {
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)
                    && t.getTransferData(DataFlavor.stringFlavor) instanceof String s) {
                return s.trim();
            }
        } catch (UnsupportedFlavorException | IOException | RuntimeException e) {
            // unreadable text
        }
        return null;
    }

    private static DataFlavor uriListFlavor() {
        try {
            return new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
