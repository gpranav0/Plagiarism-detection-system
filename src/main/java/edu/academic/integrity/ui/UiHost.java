package edu.academic.integrity.ui;

/** Operations screens use to coordinate navigation, progress, and feedback. */
public interface UiHost {
    void navigateTo(String cardName);
    void showResult(String caseId);
    void showReviewerCase(String caseId);
    void refreshAll();
    void taskStarted(String message);
    void taskProgress(String message, int percentage);
    void taskFinished(String message);
    void taskFailed(String message);
}
