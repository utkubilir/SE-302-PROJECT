package com.examplanner.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// --- BAĞIMLILIKLAR ---
// import com.examplanner.domain.*;
// import com.examplanner.services.*;
// import com.examplanner.persistence.*;

public class MainController {

    // --- FXML BİLEŞENLERİ ---
    @FXML private Button btnDataImport;
    @FXML private Button btnTimetable;
    @FXML private Button btnDashboard;
    @FXML private Button btnGenerateTimetable;

    @FXML private VBox viewDataImport;
    @FXML private VBox viewTimetable;
    @FXML private VBox viewDashboard;
    @FXML private VBox sidebar;

    // Status Labelları
    @FXML private Label lblCoursesStatus;
    @FXML private Label lblClassroomsStatus;
    @FXML private Label lblAttendanceStatus;
    @FXML private Label lblStudentsStatus;

    // --- VERİ LİSTELERİ ---
    // private List<Course> courses = new ArrayList<>();
    // private List<Classroom> classrooms = new ArrayList<>();
    // private List<Student> students = new ArrayList<>();
    // private List<Enrollment> enrollments = new ArrayList<>();
    // private ExamTimetable currentTimetable;

    // --- SERVİSLER  ---
    // private DataImportService dataImportService = new DataImportService();
    // private DataRepository repository = new DataRepository();

    @FXML
    public void initialize() {
        // Uygulama açılınca import ekranını göster
        showDataImport();

        // Veritabanı bağlantısı henüz yok, o yüzden kapalı:
        // loadDataFromDatabase();
    }

    /*
    private void loadDataFromDatabase() {
        List<Course> loadedCourses = repository.loadCourses();
        if (!loadedCourses.isEmpty()) {
            this.courses = loadedCourses;
            updateStatus(lblCoursesStatus, "Loaded from DB (" + courses.size() + ")", true);
        }
    }
    */

    // --- NAVİGASYON ---
    @FXML
    private void showDataImport() {
        setViewVisible(viewDataImport);
        setActive(btnDataImport);
        setInactive(btnTimetable);
        setInactive(btnDashboard);
    }

    @FXML
    private void showTimetable() {
        setViewVisible(viewTimetable);
        setActive(btnTimetable);
        setInactive(btnDataImport);
        setInactive(btnDashboard);
    }

    @FXML
    private void showDashboard() {
        setViewVisible(viewDashboard);
        setActive(btnDashboard);
        setInactive(btnDataImport);
        setInactive(btnTimetable);
    }

    private void setViewVisible(VBox targetView) {
        if(viewDataImport != null) viewDataImport.setVisible(false);
        if(viewTimetable != null) viewTimetable.setVisible(false);
        if(viewDashboard != null) viewDashboard.setVisible(false);

        if(targetView != null) targetView.setVisible(true);
    }

    // --- CSV YÜKLEME BUTONLARI ---
    @FXML
    private void handleLoadCourses() {
        File file = chooseFile("Load Courses CSV");
        if (file != null) {
            // courses = dataImportService.loadCourses(file); //
            // repository.saveCourses(courses);
            updateStatus(lblCoursesStatus, file.getName() + " selected (Waiting for Backend)", true);
        }
    }

    @FXML
    private void handleLoadClassrooms() {
        File file = chooseFile("Load Classrooms CSV");
        if (file != null) {
            // classrooms = dataImportService.loadClassrooms(file);
            updateStatus(lblClassroomsStatus, file.getName() + " selected (Waiting for Backend)", true);
        }
    }

    @FXML
    private void handleLoadStudents() {
        File file = chooseFile("Load Students CSV");
        if (file != null) {
            // students = dataImportService.loadStudents(file);
            updateStatus(lblStudentsStatus, file.getName() + " selected (Waiting for Backend)", true);
        }
    }

    @FXML
    private void handleLoadAttendance() {
        File file = chooseFile("Load Attendance CSV");
        if (file != null) {
            // enrollments = dataImportService.loadAttendance(file, courses, students);
            updateStatus(lblAttendanceStatus, file.getName() + " selected (Waiting for Backend)", true);
        }
    }

    // --- YARDIMCI METODLAR ---
    @FXML
    private void handleExit() {
        Platform.exit();
    }

    private File chooseFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        // Null check ekledim ki sahne hazır değilse patlamasın
        if (btnDataImport != null && btnDataImport.getScene() != null) {
            return fileChooser.showOpenDialog(btnDataImport.getScene().getWindow());
        }
        return null;
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void updateStatus(Label label, String text, boolean success) {
        if (label != null) {
            label.setText(text);
            label.setStyle(success ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        }
    }

    private void setActive(Button btn) {
        if (btn != null && !btn.getStyleClass().contains("active")) {
            btn.getStyleClass().add("active");
        }
    }

    private void setInactive(Button btn) {
        if (btn != null) {
            btn.getStyleClass().remove("active");
        }
    }

    @FXML
    private void handleGenerateTimetable() {
        System.out.println("LOG: Generate Timetable tıklandı. Backend servisi bekleniyor.");
        showError("Not Implemented", "Waiting for Scheduling Service integration.");
    }
}