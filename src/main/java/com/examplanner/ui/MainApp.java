package com.examplanner.ui;

import javafx.application.Application;
import javafx.animation.FadeTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class MainApp extends Application {

    private static Scene scene;
    private Stage splashStage;
    private Stage mainStage;

    @Override
    public void start(Stage stage) {
        this.mainStage = stage;
        showSplashScreen();
    }

    private void showSplashScreen() {
        try {
            // Load splash screen
            FXMLLoader splashLoader = new FXMLLoader(MainApp.class.getResource("SplashScreen.fxml"));
            Parent splashRoot = splashLoader.load();
            Scene splashScene = new Scene(splashRoot, 500, 350);
            splashScene.getStylesheets().add(MainApp.class.getResource("style.css").toExternalForm());

            splashStage = new Stage();
            splashStage.initStyle(StageStyle.UNDECORATED);
            splashStage.setScene(splashScene);
            splashStage.show();

            // Get progress bar and status label
            ProgressBar progressBar = (ProgressBar) splashRoot.lookup("#progressBar");
            Label lblStatus = (Label) splashRoot.lookup("#lblStatus");

            // Animate progress bar
            Task<Void> loadingTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    // Initialize database
                    updateMessage("Veritabanı başlatılıyor...");
                    updateProgress(0.2, 1.0);
                    com.examplanner.persistence.DatabaseManager.initializeDatabase();
                    Thread.sleep(500);

                    updateMessage("Arayüz yükleniyor...");
                    updateProgress(0.6, 1.0);
                    Thread.sleep(500);

                    updateMessage("Hazırlanıyor...");
                    updateProgress(1.0, 1.0);
                    Thread.sleep(300);

                    return null;
                }
            };

            progressBar.progressProperty().bind(loadingTask.progressProperty());
            lblStatus.textProperty().bind(loadingTask.messageProperty());

            loadingTask.setOnSucceeded(e -> {
                // Fade out splash screen
                FadeTransition fadeOut = new FadeTransition(Duration.millis(500), splashRoot);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(event -> {
                    splashStage.close();
                    showMainWindow();
                });
                fadeOut.play();
            });

            loadingTask.setOnFailed(e -> {
                lblStatus.textProperty().unbind();
                lblStatus.setText("Hata: " + loadingTask.getException().getMessage());
            });

            new Thread(loadingTask).start();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load splash screen: " + e.getMessage());
            // Fallback to main window
            showMainWindow();
        }
    }

    private void showMainWindow() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("MainView.fxml"));
            scene = new Scene(fxmlLoader.load(), 1200, 800);
            mainStage.setScene(scene);
            mainStage.setTitle("Exam Timetable Planner");

            // Fade in main window
            scene.getRoot().setOpacity(0);
            mainStage.show();

            FadeTransition fadeIn = new FadeTransition(Duration.millis(400), scene.getRoot());
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to start application: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
