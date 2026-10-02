package colorpicker.ui.tools;

import colorpicker.App;
import colorpicker.core.ColorChanger;
import colorpicker.core.ColorFilter;
import colorpicker.core.VB;
import colorpicker.i18n.Lang;
import colorpicker.ui.Resources;
import colorpicker.ui.UiUtil;
import colorpicker.ui.components.ColorSwatch;
import com.formdev.flatlaf.util.UIScale;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * The "Color combiner" tool (port of {@code ColorCombinerForm}): averages the
 * colours of a list. Colours are added from the main window ("Add from
 * ColorPicker", also done once when the window opens) or dropped onto the
 * list as hex text; "Select and close" sends the average to the main window.
 */
public final class ColorCombinerDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Items per row and visible rows of the list (size of the original ListView). */
    private static final int LIST_COLUMNS = 4;
    private static final int LIST_ROWS = 7;
    /**
     * Original sizes: the add button is 37 px high, the others 23 px, the colour box 20 px.
     * Fixed lengths below are at 100 % and go through UIScale.scale.
     */
    private static final float ADD_BUTTON_HEIGHT_RATIO = 37f / 23f;
    private static final float SWATCH_SIZE_RATIO = 20f / 23f;
    /** Original width of the button column (125 px at 8.25 pt, i.e. an 11 px font). */
    private static final float BUTTON_COLUMN_WIDTH_EM = 125f / 11f;

    private static ColorCombinerDialog instance;

    private final DefaultListModel<ColorItem> colors = new DefaultListModel<>();
    private final JList<ColorItem> colorList = new JList<>(colors);
    private final JScrollPane listScroll = new JScrollPane(colorList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JButton addButton = new JButton();
    private final JButton removeButton = new JButton();
    private final JLabel resultLabel = new JLabel();
    private final ColorSwatch colorPanel = new ColorSwatch(null, 20, 20);
    private final JButton cancelButton = new JButton();
    private final JButton selectButton = new JButton();
    private final transient Runnable langListener = this::updateLang;

    private ColorCombinerDialog(Window owner) {
        super(owner, ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setIconImages(Resources.appIcons());
        setResizable(false);
        buildUi();
        wireEvents();
        updateLang();
        Lang.addListener(langListener);
        // ColorCombinerForm_Shown: addButton.PerformClick()
        addCurrentColor();
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the tool window (reusing the open one), like VB's Form.Show() + BringToFront(). */
    public static void showDialog(Window owner) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new ColorCombinerDialog(owner);
        }
        instance.setVisible(true);
        instance.toFront();
    }

    private void buildUi() {
        colorList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        colorList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        colorList.setVisibleRowCount(-1);
        colorList.setCellRenderer(new ColorCellRenderer());
        listScroll.setPreferredSize(listSize());

        selectButton.setFont(selectButton.getFont().deriveFont(Font.BOLD));
        colorPanel.setColorDragEnabled(false);

        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(new EmptyBorder(UIScale.scale(new Insets(12, 12, 6, 12))));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 3;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = UIScale.scale(new Insets(0, 0, 0, 6));
        top.add(listScroll, c);

        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        top.add(addButton, c);
        c.gridy = 1;
        c.insets = UIScale.scale(new Insets(6, 0, 0, 0));
        top.add(removeButton, c);
        c.gridy = 2;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 0, 0);
        top.add(new JPanel(null), c);

        JPanel separatorPanel = new JPanel(null);
        separatorPanel.setBackground(UiUtil.DARK_GRAY);
        separatorPanel.setPreferredSize(new Dimension(1, 1));

        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setBorder(new EmptyBorder(UIScale.scale(new Insets(8, 12, 8, 12))));
        c = new GridBagConstraints();
        c.gridy = 0;
        c.gridx = 0;
        c.insets = UIScale.scale(new Insets(0, 0, 0, 6));
        bottom.add(colorPanel, c);
        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        bottom.add(resultLabel, c);
        c.weightx = 0;
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 2;
        bottom.add(cancelButton, c);
        c.gridx = 3;
        c.insets = new Insets(0, 0, 0, 0);
        bottom.add(selectButton, c);

        JPanel south = new JPanel(new BorderLayout());
        south.add(separatorPanel, BorderLayout.NORTH);
        south.add(bottom, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout());
        content.add(top, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);

        // Original TabIndex order: the select button comes before cancel.
        setFocusTraversalPolicy(new TabOrder(colorList, addButton, removeButton, selectButton, cancelButton));
    }

    /** Room for {@link #LIST_COLUMNS} x {@link #LIST_ROWS} items plus a vertical scroll bar. */
    private Dimension listSize() {
        Component cell = colorList.getCellRenderer()
                .getListCellRendererComponent(colorList, new ColorItem(new Color(0xDDDDDD)), 0, false, false);
        Dimension cellSize = cell.getPreferredSize();
        Insets in = listScroll.getInsets();
        int scrollBar = listScroll.getVerticalScrollBar().getPreferredSize().width;
        return new Dimension(cellSize.width * LIST_COLUMNS + scrollBar + in.left + in.right,
                cellSize.height * LIST_ROWS + in.top + in.bottom);
    }

    private void wireEvents() {
        addButton.addActionListener(e -> addCurrentColor());
        removeButton.addActionListener(e -> removeSelected());
        cancelButton.addActionListener(e -> dispose());
        selectButton.addActionListener(e -> selectAndClose());

        // The original polled these states with a 50 ms timer.
        colorList.addListSelectionListener(e -> updateButtons());
        colors.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                listChanged();
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                listChanged();
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                listChanged();
            }
        });
        colorList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE && e.getModifiersEx() == 0 && removeButton.isEnabled()) {
                    removeSelected();
                    e.consume();
                }
            }
        });
        new DropTarget(colorList, DnDConstants.ACTION_COPY, new ColorSwatch.HexDropListener(this::addColor), true);
    }

    /** {@code addButton.Click}: adds the colour of the main window. */
    private void addCurrentColor() {
        addColor(App.colorManager().getColor());
    }

    /** Adds a list item (also {@code colorList.DragDrop}); duplicates are allowed. */
    private void addColor(Color color) {
        colors.addElement(new ColorItem(new Color(color.getRed(), color.getGreen(), color.getBlue())));
    }

    /** {@code removeButton.Click}: removes every selected item. */
    private void removeSelected() {
        int[] selected = colorList.getSelectedIndices();
        for (int i = selected.length - 1; i >= 0; i--) {
            colors.remove(selected[i]);
        }
    }

    private void selectAndClose() {
        Color output = colorPanel.getColor();
        if (output == null) {
            return;
        }
        App.colorManager().setRGB(output);
        dispose();
    }

    private void listChanged() {
        refreshColor();
        updateButtons();
    }

    /** Average of every channel, rounded like VB's {@code Math.Round}; empty list = neutral colour. */
    private void refreshColor() {
        int count = colors.size();
        if (count == 0) {
            colorPanel.setColor(null);
            return;
        }
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        for (int i = 0; i < count; i++) {
            Color c = colors.get(i).color();
            sumR += c.getRed();
            sumG += c.getGreen();
            sumB += c.getBlue();
        }
        colorPanel.setColor(new Color(average(sumR, count), average(sumG, count), average(sumB, count)));
    }

    private static int average(long sum, int count) {
        return VB.cint(VB.round(sum / (double) count));
    }

    private void updateButtons() {
        removeButton.setEnabled(!colorList.isSelectionEmpty());
        selectButton.setEnabled(!colors.isEmpty());
    }

    /** {@code updateLang}. */
    public void updateLang() {
        setTitle(Lang.get("TOOL_COMBINER"));
        removeButton.setText(Lang.get("TOOL_COMBINER", "REMOVE"));
        cancelButton.setText(Lang.get("COMMON", "CANCEL"));
        selectButton.setText(Lang.get("COMMON", "SELECTANDCLOSE"));
        resultLabel.setText(Lang.get("COMMON", "OUTPUT"));
        layoutAddButton(Lang.get("TOOL_COMBINER", "ADD"));
        int swatch = Math.max(UIScale.scale(20),
                Math.round(selectButton.getPreferredSize().height * SWATCH_SIZE_RATIO));
        colorPanel.setPreferredSize(new Dimension(swatch, swatch));
        colorPanel.setMinimumSize(colorPanel.getPreferredSize());
        updateButtons();
        if (isDisplayable()) {
            pack();
        }
    }

    /**
     * The add button is taller than the others and, like a WinForms button,
     * wraps its text on two lines when it is wider than the button column.
     */
    private void layoutAddButton(String text) {
        removeButton.setPreferredSize(null);
        addButton.setPreferredSize(null);
        Dimension remove = removeButton.getPreferredSize();
        int column = Math.max(remove.width, Math.round(BUTTON_COLUMN_WIDTH_EM * removeButton.getFont().getSize2D()));

        addButton.setText(text);
        int padding = 0;
        if (addButton.getPreferredSize().width > column) {
            addButton.setText(twoLines(text, addButton.getFontMetrics(addButton.getFont())));
            padding = addButton.getFontMetrics(addButton.getFont()).getHeight() / 3;
        }
        Dimension add = addButton.getPreferredSize();
        int height = Math.max(add.height + padding, Math.round(remove.height * ADD_BUTTON_HEIGHT_RATIO));
        addButton.setPreferredSize(new Dimension(Math.min(add.width, column), height));
        removeButton.setPreferredSize(new Dimension(column, remove.height));
    }

    /** Splits the text at the space giving the narrowest two lines (HTML, centred). */
    private static String twoLines(String text, FontMetrics fm) {
        int best = -1;
        int bestWidth = Integer.MAX_VALUE;
        for (int i = text.indexOf(' '); i >= 0; i = text.indexOf(' ', i + 1)) {
            int w = Math.max(fm.stringWidth(text.substring(0, i)), fm.stringWidth(text.substring(i + 1)));
            if (w < bestWidth) {
                bestWidth = w;
                best = i;
            }
        }
        if (best < 0) {
            return text;
        }
        return "<html><center>" + escape(text.substring(0, best)) + "<br>" + escape(text.substring(best + 1))
                + "</center></html>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void dispose() {
        Lang.removeListener(langListener);
        if (instance == this) {
            instance = null;
        }
        super.dispose();
    }

    /** A list item (the original stored the colour in {@code ListViewItem.Tag}). */
    private record ColorItem(Color color) {
        /** Used by the list's type-ahead search and copy action. */
        @Override
        public String toString() {
            return ColorChanger.toHtml(color);
        }
    }

    /** List item: a 16x16 (scaled) colour icon with a negative border and the {@code #RRGGBB} text. */
    private static final class ColorCellRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        private final transient ColorIcon icon = new ColorIcon();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            ColorItem item = (ColorItem) value;
            super.getListCellRendererComponent(list, item.toString(), index, isSelected, cellHasFocus);
            icon.color = item.color();
            setIcon(icon);
            return this;
        }
    }

    /** {@code createColorBitmap}: filled square outlined with the negative colour. */
    private static final class ColorIcon implements Icon {
        private final int size = UIScale.scale(16);
        private Color color = Color.BLACK;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, size, size);
            g.setColor(ColorFilter.NEGATIVE.apply(color));
            g.drawRect(x, y, size - 1, size - 1);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    /** Focus order given by a fixed component list (the designer's TabIndex values). */
    private static final class TabOrder extends FocusTraversalPolicy {
        private final List<Component> order;

        TabOrder(Component... order) {
            this.order = List.of(order);
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

        private Component step(Component from, int direction) {
            int n = order.size();
            int start = from == null ? -1 : order.indexOf(from);
            if (start < 0) {
                start = direction > 0 ? -1 : n;
            }
            for (int i = 1; i <= n; i++) {
                Component c = order.get(Math.floorMod(start + direction * i, n));
                if (c.isShowing() && c.isEnabled() && c.isFocusable()) {
                    return c;
                }
            }
            return null;
        }
    }
}
