package com.example.studentdb;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

public class StudentApp extends Application {

    private TableView<Student> table = new TableView<>();
    private ObservableList<Student> studentData = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Student Database Manager");

        // Initialize Database
        DatabaseManager.initializeDatabase();

        // Load existing data
        loadDataFromDatabase();

        // Table Columns
        TableColumn<Student, String> idCol = new TableColumn<>("Student ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        // Make the column take up the full width
        idCol.prefWidthProperty().bind(table.widthProperty());

        table.getColumns().add(idCol);
        table.setItems(studentData);

        // Buttons
        Button loadCsvButton = new Button("Import CSV");
        loadCsvButton.setOnAction(e -> importCsv(primaryStage));

        Button refreshButton = new Button("Refresh Data");
        refreshButton.setOnAction(e -> loadDataFromDatabase());

        Button clearDbButton = new Button("Clear Database");
        clearDbButton.setOnAction(e -> clearDatabase());

        HBox buttonBar = new HBox(10, loadCsvButton, refreshButton, clearDbButton);
        buttonBar.setPadding(new Insets(10));

        // Layout
        BorderPane root = new BorderPane();
        root.setCenter(table);
        root.setBottom(buttonBar);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void importCsv(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Student CSV File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                List<Student> students = CsvImporter.loadStudentsFromCsv(file);
                for (Student s : students) {
                    DatabaseManager.insertStudent(s);
                }
                loadDataFromDatabase();
                showAlert("Success", "Imported " + students.size() + " students from CSV.");
            } catch (Exception e) {
                showAlert("Error", "Failed to import CSV: " + e.getMessage());
            }
        }
    }

    private void loadDataFromDatabase() {
        studentData.clear();
        List<Student> students = DatabaseManager.getAllStudents();
        studentData.addAll(students);
    }

    private void clearDatabase() {
        DatabaseManager.deleteAllStudents();
        loadDataFromDatabase();
        showAlert("Success", "All data has been deleted from the database.");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
