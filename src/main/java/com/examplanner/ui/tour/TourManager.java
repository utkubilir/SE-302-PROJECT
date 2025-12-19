package com.examplanner.ui.tour;

import javafx.scene.layout.StackPane;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class TourManager {

    private static final String PREF_TOUR_COMPLETED = "tour_completed_v1";

    private final StackPane rootContainer;
    private final List<TourStep> steps = new ArrayList<>();
    private int currentStepIndex = -1;
    private TourOverlay overlay;
    private boolean isRunning = false;

    public TourManager(StackPane rootContainer) {
        this.rootContainer = rootContainer;
        this.overlay = new TourOverlay(this::next, this::end);
    }

    public void addStep(TourStep step) {
        steps.add(step);
    }

    public void showIfFirstTime() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        boolean completed = prefs.getBoolean(PREF_TOUR_COMPLETED, false);

        if (!completed && !steps.isEmpty()) {
            start();
        }
    }

    public void forceStart() {
        start();
    }

    public void start() {
        if (steps.isEmpty())
            return;
        isRunning = true;
        currentStepIndex = -1;

        if (!rootContainer.getChildren().contains(overlay)) {
            // Bind size to root
            overlay.prefWidthProperty().bind(rootContainer.widthProperty());
            overlay.prefHeightProperty().bind(rootContainer.heightProperty());
            rootContainer.getChildren().add(overlay);
        }

        next();
    }

    public void next() {
        currentStepIndex++;
        if (currentStepIndex < steps.size()) {
            overlay.showStep(steps.get(currentStepIndex));
        } else {
            end();
        }
    }

    public void end() {
        isRunning = false;
        overlay.setVisible(false);
        rootContainer.getChildren().remove(overlay);

        // Mark as completed
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        prefs.putBoolean(PREF_TOUR_COMPLETED, true);
    }

    public boolean isRunning() {
        return isRunning;
    }
}
