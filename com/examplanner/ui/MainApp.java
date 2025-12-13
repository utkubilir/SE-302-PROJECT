package com.examplanner.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) {
        try {

            com.examplanner.persistence.DatabaseManager.initializeDatabase();
            FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("com/examplanner/ui/MainView.fxml"));
            scene = new Scene(fxmlLoader.load(), 1200, 800);
            stage.setScene(scene);
            stage.setTitle("Exam Timetable Planner");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to start application: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
