package com.examplanner.ui.tour;

import javafx.animation.FadeTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

public class TourOverlay extends Pane {

    private final Runnable onNext;
    private final Runnable onSkip;

    private VBox messageBubble;
    private Label lblTitle;
    private Label lblDescription;
    private Button btnSkip;
    private Button btnNext;
    private Shape spotlightMask;
    private javafx.scene.shape.Polygon arrow;

    private TourStep currentStep; // Track current step for resize/interaction

    public TourOverlay(Runnable onNext, Runnable onSkip) {
        this.onNext = onNext;
        this.onSkip = onSkip;

        initializeUI();

        // Handle window resize
        widthProperty().addListener((obs, oldVal, newVal) -> refreshLayout());
        heightProperty().addListener((obs, oldVal, newVal) -> refreshLayout());

        // Handle interactions
        this.setOnMouseClicked(e -> {
            // Check if click is inside the target hole (click-through)
            if (currentStep != null && isClickOnTarget(e.getX(), e.getY())) {
                e.consume();
                // Trigger the target action if it's a button
                Node target = currentStep.targetNode();
                if (target instanceof Button) {
                    ((Button) target).fire();
                }
                // Advance tour
                onNext.run();
            } else {
                // Consume other clicks to block app interaction
                e.consume();
            }
        });
    }

    private void initializeUI() {
        // Message Bubble Container
        messageBubble = new VBox(10);
        messageBubble.setPadding(new Insets(15));
        messageBubble.setMaxWidth(300);
        messageBubble.getStyleClass().add("tour-bubble");

        // Arrow pointer
        arrow = new javafx.scene.shape.Polygon();
        arrow.getStyleClass().add("tour-arrow");
        arrow.setFill(Color.web("#ffffff")); // Default match bubble bg (updated in CSS)

        // Title
        lblTitle = new Label();
        lblTitle.getStyleClass().add("tour-title");
        lblTitle.setWrapText(true);

        // Description
        lblDescription = new Label();
        lblDescription.getStyleClass().add("tour-description");
        lblDescription.setWrapText(true);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        btnSkip = new Button("Skip");
        btnSkip.getStyleClass().add("tour-btn-skip");
        btnSkip.setOnAction(e -> onSkip.run());

        btnNext = new Button("Next");
        btnNext.getStyleClass().add("tour-btn-next");
        btnNext.setOnAction(e -> onNext.run());

        buttonBox.getChildren().addAll(btnSkip, btnNext);

        messageBubble.getChildren().addAll(lblTitle, lblDescription, buttonBox);
        getChildren().addAll(maskGroup(), arrow, messageBubble); // Order matters

        // Initial visibility
        setVisible(false);
        // Initial visibility
        setVisible(false);
    }

    public void setButtonLabels(String skipText, String nextText) {
        if (btnSkip != null)
            btnSkip.setText(skipText);
        if (btnNext != null)
            btnNext.setText(nextText);
    }

    public void showStep(TourStep step) {
        this.currentStep = step;
        if (step == null)
            return;

        // Update content
        lblTitle.setText(step.title());
        lblDescription.setText(step.description());

        refreshLayout();

        FadeTransition ft = new FadeTransition(Duration.millis(300), this);
        ft.setFromValue(0);
        ft.setToValue(1);
        setVisible(true);
        ft.play();

        // Accessibility focus
        messageBubble.requestFocus();
    }

    private void refreshLayout() {
        if (currentStep == null)
            return;

        Node target = currentStep.targetNode();
        if (target != null && target.getScene() != null) {
            updateSpotlight(target);
            updateBubblePosition(target, currentStep.position());
        } else {
            removeSpotlight();
            centerBubble();
            arrow.setVisible(false);
        }
    }

    private javafx.scene.Group maskGroup() {
        return new javafx.scene.Group(); // Placeholder if needed, but we add Shape directly
    }

    private void updateSpotlight(Node target) {
        // Remove old mask
        if (spotlightMask != null) {
            getChildren().remove(spotlightMask);
        }

        Bounds bounds = target.localToScene(target.getBoundsInLocal());

        // Create full screen rectangle (semi-transparent black)
        Rectangle screen = new Rectangle(getWidth(), getHeight());

        // Create hole (target area)
        double padding = 5;
        Rectangle hole = new Rectangle(
                bounds.getMinX() - padding,
                bounds.getMinY() - padding,
                bounds.getWidth() + (padding * 2),
                bounds.getHeight() + (padding * 2));

        // Alternatively use Circle for icon buttons if desired,
        // but Rectangle is safer for generic targets.
        hole.setArcWidth(10);
        hole.setArcHeight(10);

        // Subtract hole from screen
        spotlightMask = Shape.subtract(screen, hole);
        spotlightMask.setFill(Color.rgb(0, 0, 0, 0.7));

        // Add mask at the bottom (index 0) so bubble stays on top
        getChildren().add(0, spotlightMask);
    }

    private void removeSpotlight() {
        if (spotlightMask != null) {
            getChildren().remove(spotlightMask);
            spotlightMask = null;
        }
    }

    private void updateBubblePosition(Node target, TourStep.TourPosition position) {
        Bounds bounds = target.localToScene(target.getBoundsInLocal());

        // Force layout to get bubble dimensions
        messageBubble.applyCss();
        messageBubble.layout();

        double bubbleWidth = messageBubble.getWidth();
        double bubbleHeight = messageBubble.getHeight();

        double x = 0;
        double y = 0;
        double spacing = 15;

        switch (position) {
            case RIGHT:
                x = bounds.getMaxX() + spacing;
                y = bounds.getMinY() + (bounds.getHeight() / 2) - (bubbleHeight / 2);
                break;
            case LEFT:
                x = bounds.getMinX() - bubbleWidth - spacing;
                y = bounds.getMinY() + (bounds.getHeight() / 2) - (bubbleHeight / 2);
                break;
            case BOTTOM:
                x = bounds.getMinX() + (bounds.getWidth() / 2) - (bubbleWidth / 2);
                y = bounds.getMaxY() + spacing;
                break;
            case TOP:
                x = bounds.getMinX() + (bounds.getWidth() / 2) - (bubbleWidth / 2);
                y = bounds.getMinY() - bubbleHeight - spacing;
                break;
            case CENTER:
            default:
                centerBubble();
                return;
        }

        // Boundary checks to keep bubble on screen
        if (x < 10)
            x = 10;
        if (y < 10)
            y = 10;
        if (x + bubbleWidth > getWidth() - 10)
            x = getWidth() - bubbleWidth - 10;
        if (y + bubbleHeight > getHeight() - 10)
            y = getHeight() - bubbleHeight - 10;

        messageBubble.setLayoutX(x);
        messageBubble.setLayoutY(y);

        updateArrow(x, y, bubbleWidth, bubbleHeight, bounds, position);
    }

    private void updateArrow(double bx, double by, double bw, double bh, Bounds targetBounds,
            TourStep.TourPosition pos) {
        arrow.setVisible(true);
        arrow.getPoints().clear();

        double arrowSize = 10;

        // Points depend on position relative to bubble
        switch (pos) {
            case RIGHT: // Bubble is to the Right of target -> Arrow points LEFT
                arrow.getPoints().addAll(
                        bx, by + 20,
                        bx - arrowSize, by + 20 + (arrowSize / 2),
                        bx, by + 20 + arrowSize);
                break;
            case LEFT: // Bubble is Left -> Arrow points RIGHT
                arrow.getPoints().addAll(
                        bx + bw, by + 20,
                        bx + bw + arrowSize, by + 20 + (arrowSize / 2),
                        bx + bw, by + 20 + arrowSize);
                break;
            case BOTTOM: // Bubble is Bottom -> Arrow points UP
                arrow.getPoints().addAll(
                        bx + (bw / 2) - (arrowSize / 2), by,
                        bx + (bw / 2), by - arrowSize,
                        bx + (bw / 2) + (arrowSize / 2), by);
                break;
            case TOP: // Bubble is Top -> Arrow points DOWN
                arrow.getPoints().addAll(
                        bx + (bw / 2) - (arrowSize / 2), by + bh,
                        bx + (bw / 2), by + bh + arrowSize,
                        bx + (bw / 2) + (arrowSize / 2), by + bh);
                break;
            default:
                arrow.setVisible(false);
        }

        // Ensure arrow fill matches bubble (could be dynamic based on theme)
        // For now hardcoded to match style.css assumption, or better: bind to bubble
        // background
        // arrow.fillProperty().bind(messageBubble.backgroundProperty()...); // Complex
        // due to CSS lookup
    }

    private boolean isClickOnTarget(double x, double y) {
        if (currentStep == null || currentStep.targetNode() == null)
            return false;
        Node target = currentStep.targetNode();
        Bounds bounds = target.localToScene(target.getBoundsInLocal());
        return bounds.contains(x, y);
    }

    private void centerBubble() {
        // Force layout
        messageBubble.applyCss();
        messageBubble.layout();

        messageBubble.setLayoutX((getWidth() - messageBubble.getWidth()) / 2);
        messageBubble.setLayoutY((getHeight() - messageBubble.getHeight()) / 2);
    }
}
