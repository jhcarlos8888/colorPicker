package colorpicker;

import colorpicker.i18n.Lang;
import colorpicker.i18n.Language;
import colorpicker.settings.AppSettings;
import colorpicker.ui.MainWindow;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.GraphicsEnvironment;
import java.util.Locale;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point of ColorPicker for Linux.
 */
public final class Main {

    /** A later launch asked for the window before it existed (EDT only). */
    private static boolean pendingActivate;

    private Main() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("ColorPicker needs a graphical display and a Java runtime with "
                    + "graphics support (e.g. sudo apt install openjdk-17-jre).");
            System.exit(1);
        }
        LinuxIntegration.init();
        // Claimed before anything slow, so that a quick second launch is forwarded here.
        if (!SingleInstance.claim(() -> SwingUtilities.invokeLater(Main::activate))) {
            return;
        }
        if (System.getProperty("awt.useSystemAAFontSettings") == null) {
            System.setProperty("awt.useSystemAAFontSettings", "on");
        }
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("ColorPicker: uncaught exception in " + t.getName());
            e.printStackTrace();
        });
        SwingUtilities.invokeLater(Main::start);
    }

    private static void start() {
        setupLookAndFeel();

        AppSettings settings = AppSettings.get();
        // First start: use the system language (Form1_Load in the original).
        if (settings.isFirstTime()) {
            settings.setLanguage(Language.fromLocale(Locale.getDefault()).code());
            settings.setFirstTime(false);
            settings.save();
        }
        Language language = Language.fromCode(settings.getLanguage());
        if (language == null) {
            language = Language.ENGLISH;
            settings.setLanguage(language.code());
        }
        Lang.load(language);

        MainWindow window = new MainWindow();
        App.setMainWindow(window);
        window.setVisible(true);
        if (pendingActivate) {
            pendingActivate = false;
            window.bringToFront();
        }
    }

    /** Single-instance activation by a later launch (on the EDT). */
    private static void activate() {
        MainWindow window = App.mainWindow();
        if (window != null) {
            window.bringToFront();
        } else {
            pendingActivate = true;
        }
    }

    private static void setupLookAndFeel() {
        try {
            FlatLightLaf.setup();
        } catch (RuntimeException | LinkageError e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep the default look and feel
            }
        }
    }
}
