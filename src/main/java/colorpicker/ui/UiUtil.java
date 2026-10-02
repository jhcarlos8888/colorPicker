package colorpicker.ui;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.IOException;
import java.net.URI;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * Small helpers shared by all windows: colours used for validation, clipboard,
 * browser and text formatting.
 */
public final class UiUtil {

    /** {@code Color.Tomato}: background of an invalid text box. */
    public static final Color TOMATO = new Color(255, 99, 71);
    /** {@code Color.LemonChiffon}: "from" greater than "to" in the random tool. */
    public static final Color LEMON_CHIFFON = new Color(255, 250, 205);
    /** {@code Color.DarkGray} (.NET's DarkGray is 169,169,169): placeholder text. */
    public static final Color DARK_GRAY = new Color(169, 169, 169);

    private UiUtil() {
    }

    /** Normal background of an editable text field ({@code SystemColors.Window}). */
    public static Color windowBackground() {
        Color c = UIManager.getColor("TextField.background");
        return c != null ? c : Color.WHITE;
    }

    /** Normal text colour ({@code SystemColors.ControlText}). */
    public static Color controlText() {
        Color c = UIManager.getColor("TextField.foreground");
        return c != null ? c : Color.BLACK;
    }

    /** Neutral panel background ({@code SystemColors.Control}). */
    public static Color controlBackground() {
        Color c = UIManager.getColor("Panel.background");
        return c != null ? c : new Color(240, 240, 240);
    }

    /**
     * Converts a text that may contain {@code \n} into something Swing labels,
     * buttons and tooltips render on several lines.
     */
    public static String multiline(String text) {
        if (text == null || text.indexOf('\n') < 0) {
            return text;
        }
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html>" + escaped.replace("\n", "<br>") + "</html>";
    }

    /** Tooltip with a bold title line (WinForms {@code ToolTip.ToolTipTitle}). */
    public static String tooltip(String title, String text) {
        String t = text == null ? "" : text;
        String escaped = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        if (title == null || title.isEmpty()) {
            return "<html>" + escaped + "</html>";
        }
        String escTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><b>" + escTitle + "</b><br>" + escaped + "</html>";
    }

    /** {@code Clipboard.SetText}. */
    public static void setClipboardText(String text) {
        StringSelection sel = new StringSelection(text);
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
        } catch (IllegalStateException e) {
            System.err.println("ColorPicker: clipboard unavailable: " + e);
        }
    }

    /** {@code Clipboard.GetText}, or an empty string when the clipboard has no text. */
    public static String getClipboardText() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object data = t.getTransferData(DataFlavor.stringFlavor);
                return data == null ? "" : data.toString();
            }
        } catch (Exception e) {
            // clipboard busy or content not readable: treat as empty
        }
        return "";
    }

    /** {@code Process.Start(url)}: opens a web page in the default browser. */
    public static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (IOException | UnsupportedOperationException e) {
            // fall back to xdg-open below
        }
        try {
            new ProcessBuilder("xdg-open", url).inheritIO().start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, url, "ColorPicker", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Short system beep ({@code SystemSounds.Exclamation}). */
    public static void beep() {
        Toolkit.getDefaultToolkit().beep();
    }
}
