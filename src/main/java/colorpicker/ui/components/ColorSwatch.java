package colorpicker.ui.components;

import colorpicker.core.ColorChanger;
import colorpicker.core.ColorFilter;
import colorpicker.ui.CursorFactory;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.InvalidDnDOperationException;
import java.util.function.Consumer;
import javax.swing.JComponent;

/**
 * A colour box (the {@code colorPanel} panels of the original forms): filled
 * with a colour and outlined with its negative.
 *
 * <ul>
 * <li>Dragging it with the left button drags its colour as {@code #RRGGBB}
 * text, showing a colour cursor ({@code Panel1_MouseMove} /
 * {@code Panel1_GiveFeedback}). Works with other applications too.</li>
 * <li>Optionally a drop target: {@link #setDropHandler(Consumer)} accepts
 * dropped text that is a valid hex colour ({@code Panel1_DragEnter} /
 * {@code Panel1_DragDrop}).</li>
 * <li>A {@code null} colour paints the neutral panel background (the
 * original's {@code SystemColors.Control}) and disables dragging.</li>
 * </ul>
 * Use {@link #setComponentPopupMenu} for the right-click menu.
 */
public class ColorSwatch extends JComponent {

    private static final long serialVersionUID = 1L;

    private Color color;
    private Consumer<Color> dropHandler;
    private boolean dragEnabled = true;

    public ColorSwatch(Color initial, int width, int height) {
        this.color = initial;
        Dimension d = new Dimension(width, height);
        setPreferredSize(d);
        setMinimumSize(d);
        setOpaque(true);
        installDragSource();
    }

    public Color getColor() {
        return color;
    }

    /** Sets the colour ({@code null} = empty) and repaints; fires {@code "color"} property change. */
    public void setColor(Color c) {
        Color old = this.color;
        this.color = c == null ? null : new Color(c.getRed(), c.getGreen(), c.getBlue());
        repaint();
        firePropertyChange("color", old, this.color);
    }

    public void setColorDragEnabled(boolean enabled) {
        this.dragEnabled = enabled;
    }

    /**
     * Makes the swatch accept dropped hex colour text. The handler receives the
     * parsed colour; {@code null} removes the drop target.
     */
    public void setDropHandler(Consumer<Color> handler) {
        this.dropHandler = handler;
        if (handler == null) {
            setDropTarget(null);
        } else if (getDropTarget() == null) {
            new DropTarget(this, DnDConstants.ACTION_COPY, new HexDropListener(c -> {
                if (dropHandler != null) {
                    dropHandler.accept(c);
                }
            }), true);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color bg = color != null ? color : colorpicker.ui.UiUtil.controlBackground();
        g.setColor(bg);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(ColorFilter.NEGATIVE.apply(bg));
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
    }

    private void installDragSource() {
        DragSource ds = DragSource.getDefaultDragSource();
        ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY, this::startDrag);
    }

    private void startDrag(DragGestureEvent dge) {
        if (!dragEnabled || color == null || !isEnabled()) {
            return;
        }
        if (dge.getTriggerEvent() instanceof java.awt.event.MouseEvent me
                && !javax.swing.SwingUtilities.isLeftMouseButton(me)) {
            return;
        }
        Color dragged = color;
        Cursor colorCursor = CursorFactory.colorCursor(dragged);
        try {
            dge.startDrag(colorCursor, new StringSelection(ColorChanger.toHtml(dragged)), new DragSourceAdapter() {
                @Override
                public void dragEnter(DragSourceDragEvent e) {
                    update(e);
                }

                @Override
                public void dragOver(DragSourceDragEvent e) {
                    update(e);
                }

                @Override
                public void dropActionChanged(DragSourceDragEvent e) {
                    update(e);
                }

                @Override
                public void dragExit(DragSourceEvent e) {
                    e.getDragSourceContext().setCursor(DragSource.DefaultCopyNoDrop);
                }

                private void update(DragSourceDragEvent e) {
                    boolean copy = (e.getDropAction() & DnDConstants.ACTION_COPY) != 0;
                    e.getDragSourceContext().setCursor(copy ? colorCursor : DragSource.DefaultCopyNoDrop);
                }
            });
        } catch (InvalidDnDOperationException e) {
            // another drag is already in progress
        }
    }

    /**
     * Drop listener accepting text that is a valid {@code #RRGGBB} /
     * {@code #RGB} colour. Reusable for other drop targets (e.g. the colour
     * combiner list).
     */
    public static final class HexDropListener extends DropTargetAdapter {
        private final Consumer<Color> onDrop;

        public HexDropListener(Consumer<Color> onDrop) {
            this.onDrop = onDrop;
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
            String text = textOf(e.getTransferable());
            if (text != null && ColorChanger.isValidHexColorCode(text.trim())) {
                e.acceptDrag(DnDConstants.ACTION_COPY);
            } else {
                e.rejectDrag();
            }
        }

        @Override
        public void drop(DropTargetDropEvent e) {
            if (!e.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                e.rejectDrop();
                return;
            }
            e.acceptDrop(DnDConstants.ACTION_COPY);
            String text = textOf(e.getTransferable());
            Color c = text == null || !ColorChanger.isValidHexColorCode(text.trim())
                    ? null : ColorChanger.fromHtml(text.trim());
            if (c != null) {
                onDrop.accept(c);
            }
            e.dropComplete(c != null);
        }

        /**
         * During a drag (before the drop) the transferable of a foreign
         * application may be unavailable; {@code null} is returned then.
         */
        private static String textOf(Transferable t) {
            if (t == null || !t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return null;
            }
            try {
                Object data = t.getTransferData(DataFlavor.stringFlavor);
                return data == null ? null : data.toString();
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
