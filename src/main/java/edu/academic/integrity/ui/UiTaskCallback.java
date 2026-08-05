package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.service.ProgressUpdate;
import javax.swing.SwingUtilities;

/** Marshals headless controller callbacks onto Swing's event-dispatch thread. */
public abstract class UiTaskCallback<T> implements ApplicationController.TaskCallback<T> {
    private final UiHost host;
    private final String successMessage;

    protected UiTaskCallback(UiHost host, String successMessage) {
        this.host = host;
        this.successMessage = successMessage;
    }

    @Override
    public final void onProgress(ProgressUpdate update) {
        SwingUtilities.invokeLater(() -> {
            host.taskProgress(update == null ? "Working…" : update.message(),
                    update == null ? -1 : update.percent());
            handleProgress(update);
        });
    }

    @Override
    public final void onSuccess(T result) {
        SwingUtilities.invokeLater(() -> {
            host.taskFinished(successMessage);
            host.refreshAll();
            handleSuccess(result);
        });
    }

    @Override
    public final void onFailure(String friendlyMessage) {
        SwingUtilities.invokeLater(() -> {
            host.taskFailed(friendlyMessage);
            handleFailure(friendlyMessage);
        });
    }

    @Override
    public final void onCancelled() {
        SwingUtilities.invokeLater(() -> {
            host.taskFinished("Operation cancelled");
            handleCancellation();
        });
    }

    protected abstract void handleSuccess(T result);

    protected void handleProgress(ProgressUpdate update) { }

    protected void handleFailure(String message) { }

    protected void handleCancellation() { }
}
