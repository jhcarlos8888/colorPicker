package colorpicker;

import java.awt.AWTError;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.lang.reflect.Field;

/**
 * Desktop integration for Linux (no counterpart in the VB.NET original).
 *
 * <p>On X11 it sets the {@code WM_CLASS} of every window to
 * {@value #WM_CLASS}, the value of {@code StartupWMClass} in the installed
 * {@code .desktop} file, so that panels such as the Cinnamon one group the
 * windows under the menu entry and show its icon. AWT has no public API for
 * this, hence the reflective write of {@code sun.awt.X11.XToolkit.awtAppClassName};
 * it requires {@code --add-opens java.desktop/sun.awt.X11=ALL-UNNAMED} (given by
 * the launchers and the jar manifest) and is silently skipped when that is not
 * permitted or the toolkit is not X11.
 */
public final class LinuxIntegration {

    /** WM_CLASS of the application windows (= StartupWMClass). */
    public static final String WM_CLASS = "ColorPicker";

    private LinuxIntegration() {
    }

    /** Must run before the first window is created. */
    public static void init() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return;
            }
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            if (!"sun.awt.X11.XToolkit".equals(toolkit.getClass().getName())) {
                return;
            }
            Field field = toolkit.getClass().getDeclaredField("awtAppClassName");
            field.setAccessible(true);
            field.set(null, WM_CLASS);
        } catch (ReflectiveOperationException | RuntimeException | AWTError | LinkageError e) {
            // Not permitted (missing --add-opens), not X11 or no display: keep the default.
        }
    }
}
