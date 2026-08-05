package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import java.io.File;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Creates the headless controller first, then shows Swing exclusively on the EDT. */
public final class MainApplication {
    private MainApplication() { }

    public static void launch(File projectRoot) throws Exception {
        Theme.install();
        ApplicationController controller = new ApplicationController(projectRoot);
        SwingUtilities.invokeLater(() -> {
            try {
                MainFrame frame = new MainFrame(controller);
                frame.setVisible(true);
            } catch (RuntimeException failure) {
                controller.logDetailedError("Desktop interface startup failed", failure);
                controller.shutdown();
                JOptionPane.showMessageDialog(null,
                        "The desktop interface could not be started.\n"
                                + "Check logs/errors.log for details.",
                        "Startup failure", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
