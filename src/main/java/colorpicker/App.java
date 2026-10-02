package colorpicker;

import colorpicker.core.ColorManager;
import colorpicker.ui.MainWindow;

/**
 * Application-wide singletons, the Java counterpart of VB's default form
 * instances ({@code Form1.colormanager}, ...). Everything here is used on the
 * Swing event dispatch thread only.
 */
public final class App {

    private static final ColorManager COLOR_MANAGER = new ColorManager();
    private static MainWindow mainWindow;

    private App() {
    }

    /** The colour being edited ({@code Form1.colormanager}). */
    public static ColorManager colorManager() {
        return COLOR_MANAGER;
    }

    /** The main window ({@code Form1}), or {@code null} before start-up. */
    public static MainWindow mainWindow() {
        return mainWindow;
    }

    static void setMainWindow(MainWindow window) {
        mainWindow = window;
    }
}
