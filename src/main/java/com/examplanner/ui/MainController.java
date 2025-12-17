package com.examplanner.ui;

import com.examplanner.domain.Classroom;
import com.examplanner.domain.Course;
import com.examplanner.domain.Enrollment;
import com.examplanner.domain.Exam;
import com.examplanner.domain.Student;
import com.examplanner.domain.ExamTimetable;
import com.examplanner.services.DataImportService;
import com.examplanner.services.SchedulerService;
import javafx.concurrent.Task;
import java.time.LocalTime;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.stage.Modality;
import javafx.stage.StageStyle;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import java.util.Comparator;
import java.io.PrintWriter;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

public class MainController {

    @FXML
    private Button btnDataImport;
    @FXML
    private Button btnTimetable;
    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnFilter;
    @FXML
    private Button btnStudentPortal;

    @FXML
    private Button btnGenerateDataImport;
    @FXML
    private Button btnGenerateTimetable;
    @FXML
    private Button btnDeleteData;

    @FXML
    private VBox viewDataImport;
    @FXML
    private VBox viewTimetable;
    @FXML
    private VBox sidebar;
    @FXML
    private VBox viewDashboard;
    @FXML
    private VBox viewUserManual;
    @FXML
    private Button btnUserManual;
    @FXML
    private javafx.scene.chart.BarChart<String, Number> chartExamsPerDay;
    @FXML
    private javafx.scene.chart.PieChart chartRoomUsage;

    @FXML
    private Label lblCoursesStatus;
    @FXML
    private Label lblClassroomsStatus;
    @FXML
    private Label lblAttendanceStatus;
    @FXML
    private Label lblStudentsStatus;

    @FXML
    private TableView<Exam> examTableView;
    @FXML
    private TableColumn<Exam, String> colExamId;
    @FXML
    private TableColumn<Exam, String> colCourseCode;
    @FXML
    private TableColumn<Exam, String> colDay;
    @FXML
    private TableColumn<Exam, String> colTimeSlot;
    @FXML
    private TableColumn<Exam, String> colClassroom;
    @FXML
    private TableColumn<Exam, Integer> colStudents;
    @FXML
    private TableColumn<Exam, Void> colActions;

    @FXML
    private javafx.scene.control.TextField txtCourseSearch;

    @FXML
    private javafx.scene.control.ProgressBar progressBar;
    @FXML
    private Label lblProgressStatus;
    @FXML
    private VBox progressContainer;

    private List<Course> courses = new ArrayList<>();
    private List<Classroom> classrooms = new ArrayList<>();
    private List<Student> students = new ArrayList<>();
    private List<Enrollment> enrollments = new ArrayList<>();

    private ExamTimetable currentTimetable;

    private DataImportService dataImportService = new DataImportService();
    private SchedulerService schedulerService = new SchedulerService();
    private com.examplanner.persistence.DataRepository repository = new com.examplanner.persistence.DataRepository();
    private com.examplanner.services.ConstraintChecker constraintChecker = new com.examplanner.services.ConstraintChecker();

    @FXML
    public void initialize() {
        constraintChecker.setMinGapMinutes(180); // Default to requirements
        showDataImport();

        // Setup course search listener
        if (txtCourseSearch != null) {
            txtCourseSearch.textProperty().addListener((observable, oldValue, newValue) -> {
                filterTableByCourseCode(newValue);
            });
        }

        // Load data from DB
        List<Course> loadedCourses = repository.loadCourses();
        if (!loadedCourses.isEmpty()) {
            this.courses = loadedCourses;
            lblCoursesStatus.setText("Loaded from DB (" + courses.size() + ")");
            lblCoursesStatus.setStyle("-fx-text-fill: green;");
        }

        List<Classroom> loadedClassrooms = repository.loadClassrooms();
        if (!loadedClassrooms.isEmpty()) {
            this.classrooms = loadedClassrooms;
            lblClassroomsStatus.setText("Loaded from DB (" + classrooms.size() + ")");
            lblClassroomsStatus.setStyle("-fx-text-fill: green;");
        }

        List<Student> loadedStudents = repository.loadStudents();
        if (!loadedStudents.isEmpty()) {
            this.students = loadedStudents;
            lblStudentsStatus.setText("Loaded from DB (" + students.size() + ")");
            lblStudentsStatus.setStyle("-fx-text-fill: green;");
        }

        // Enrollments depend on students and courses
        if (!students.isEmpty() && !courses.isEmpty()) {
            List<Enrollment> loadedEnrollments = repository.loadEnrollments(students, courses);
            if (!loadedEnrollments.isEmpty()) {
                this.enrollments = loadedEnrollments;
                lblAttendanceStatus.setText("Loaded from DB (" + enrollments.size() + ")");
                lblAttendanceStatus.setStyle("-fx-text-fill: green;");

                // Load Timetable if everything else is present
                ExamTimetable loadedTimetable = repository.loadTimetable(courses, classrooms, enrollments);
                if (loadedTimetable != null) {
                    this.currentTimetable = loadedTimetable;
                    refreshTimetable();
                }
            }
        }
    }

    private void filterTableByCourseCode(String searchText) {
        if (currentTimetable == null || currentTimetable.getExams().isEmpty()) {
            return;
        }

        if (searchText == null || searchText.trim().isEmpty()) {
            // Show all exams
            List<Exam> sortedExams = new ArrayList<>(currentTimetable.getExams());
            sortedExams.sort(Comparator.comparing((Exam e) -> e.getSlot().getDate())
                    .thenComparing(e -> e.getSlot().getStartTime()));
            examTableView.setItems(FXCollections.observableArrayList(sortedExams));
        } else {
            // Filter by course code
            String lowerSearch = searchText.toLowerCase().trim();
            List<Exam> filteredExams = currentTimetable.getExams().stream()
                    .filter(e -> e.getCourse().getCode().toLowerCase().contains(lowerSearch))
                    .sorted(Comparator.comparing((Exam e) -> e.getSlot().getDate())
                            .thenComparing(e -> e.getSlot().getStartTime()))
                    .collect(Collectors.toList());
            examTableView.setItems(FXCollections.observableArrayList(filteredExams));
        }
    }

    @FXML
    private void showDataImport() {
        viewDataImport.setVisible(true);
        viewTimetable.setVisible(false);
        viewDashboard.setVisible(false);
        viewUserManual.setVisible(false);
        if (sidebar != null) {
            sidebar.setVisible(true);
            sidebar.setManaged(true);
        }
        setActive(btnDataImport);
        setInactive(btnTimetable);
        setInactive(btnDashboard);
        setInactive(btnUserManual);
    }

    @FXML
    private void showDashboard() {
        viewDataImport.setVisible(false);
        viewTimetable.setVisible(false);
        viewDashboard.setVisible(true);
        viewUserManual.setVisible(false);

        if (sidebar != null) {
            sidebar.setVisible(true);
            sidebar.setManaged(true);
        }

        setActive(btnDashboard);
        setInactive(btnDataImport);
        setInactive(btnTimetable);
        setInactive(btnUserManual);

        refreshDashboard();
    }

    @FXML
    private void showTimetable() {
        viewDataImport.setVisible(false);
        viewDashboard.setVisible(false);
        viewTimetable.setVisible(true);
        viewUserManual.setVisible(false);

        if (sidebar != null) {
            sidebar.setVisible(false);
            sidebar.setManaged(false);
        }
        setActive(btnTimetable);
        setInactive(btnDataImport);
        setInactive(btnDashboard);
        setInactive(btnUserManual);
        refreshTimetable();
    }

    @FXML
    private void showUserManual() {
        viewDataImport.setVisible(false);
        viewDashboard.setVisible(false);
        viewTimetable.setVisible(false);
        viewUserManual.setVisible(true);

        if (sidebar != null) {
            sidebar.setVisible(true);
            sidebar.setManaged(true);
        }
        setActive(btnUserManual);
        setInactive(btnDataImport);
        setInactive(btnDashboard);
        setInactive(btnTimetable);
    }

    private void setActive(Button btn) {
        if (!btn.getStyleClass().contains("active")) {
            btn.getStyleClass().add("active");
        }
    }

    private void setInactive(Button btn) {
        btn.getStyleClass().remove("active");
    }

    @FXML
    private void handleLoadCourses() {
        File file = chooseFile("Load Courses CSV");
        if (file != null) {
            try {
                // Clear previous style classes
                lblCoursesStatus.getStyleClass().removeAll("text-success", "text-warning", "text-error");

                courses = dataImportService.loadCourses(file);
                repository.saveCourses(courses);

                if (courses.isEmpty()) {
                    lblCoursesStatus.setText("No courses found in file");
                    lblCoursesStatus.getStyleClass().add("text-warning");
                    showWarning("Empty Import", "No valid courses found in the file.");
                } else {
                    lblCoursesStatus.setText(file.getName() + " • " + courses.size() + " courses loaded");
                    lblCoursesStatus.getStyleClass().add("text-success");
                }
            } catch (IllegalArgumentException e) {
                showWarning("Import Error", e.getMessage());
                lblCoursesStatus.setText("Import failed");
                lblCoursesStatus.getStyleClass().add("text-warning");
            } catch (com.examplanner.persistence.DataAccessException e) {
                showError("Database Error", "Failed to save courses to database:\n" + e.getMessage());
                lblCoursesStatus.setText("Database error");
                lblCoursesStatus.getStyleClass().add("text-error");
            } catch (Exception e) {
                showError("Error loading courses",
                        "Your file may be empty or formatted incorrectly.\nError: " + e.getMessage());
                lblCoursesStatus.setText("Import failed");
                lblCoursesStatus.getStyleClass().add("text-error");
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleLoadClassrooms() {
        File file = chooseFile("Load Classrooms CSV");
        if (file != null) {
            try {
                // Clear previous style classes
                lblClassroomsStatus.getStyleClass().removeAll("text-success", "text-warning", "text-error");

                classrooms = dataImportService.loadClassrooms(file);
                repository.saveClassrooms(classrooms);

                if (classrooms.isEmpty()) {
                    lblClassroomsStatus.setText("No classrooms found in file");
                    lblClassroomsStatus.getStyleClass().add("text-warning");
                    showWarning("Empty Import", "No valid classrooms found in the file.");
                } else {
                    lblClassroomsStatus.setText(file.getName() + " • " + classrooms.size() + " classrooms loaded");
                    lblClassroomsStatus.getStyleClass().add("text-success");
                }
            } catch (IllegalArgumentException e) {
                showWarning("Import Error", e.getMessage());
                lblClassroomsStatus.setText("Import failed");
                lblClassroomsStatus.getStyleClass().add("text-warning");
            } catch (com.examplanner.persistence.DataAccessException e) {
                showError("Database Error", "Failed to save classrooms to database:\n" + e.getMessage());
                lblClassroomsStatus.setText("Database error");
                lblClassroomsStatus.getStyleClass().add("text-error");
            } catch (Exception e) {
                showError("Error loading classrooms",
                        "Your file may be empty or formatted incorrectly.\nError: " + e.getMessage());
                lblClassroomsStatus.setText("Import failed");
                lblClassroomsStatus.getStyleClass().add("text-error");
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleLoadStudents() {
        File file = chooseFile("Load Students CSV");
        if (file != null) {
            try {
                // Clear previous style classes
                lblStudentsStatus.getStyleClass().removeAll("text-success", "text-warning", "text-error");

                students = dataImportService.loadStudents(file);
                repository.saveStudents(students);

                if (students.isEmpty()) {
                    lblStudentsStatus.setText("No students found in file");
                    lblStudentsStatus.getStyleClass().add("text-warning");
                    showWarning("Empty Import", "No valid students found in the file.");
                } else {
                    lblStudentsStatus.setText(file.getName() + " • " + students.size() + " students loaded");
                    lblStudentsStatus.getStyleClass().add("text-success");
                }
            } catch (IllegalArgumentException e) {
                showWarning("Import Error", e.getMessage());
                lblStudentsStatus.setText("Import failed");
                lblStudentsStatus.getStyleClass().add("text-warning");
            } catch (com.examplanner.persistence.DataAccessException e) {
                showError("Database Error", "Failed to save students to database:\n" + e.getMessage());
                lblStudentsStatus.setText("Database error");
                lblStudentsStatus.getStyleClass().add("text-error");
            } catch (Exception e) {
                showError("Error loading students",
                        "Your file may be empty or formatted incorrectly.\nError: " + e.getMessage());
                lblStudentsStatus.setText("Import failed");
                lblStudentsStatus.getStyleClass().add("text-error");
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleLoadAttendance() {
        if (courses.isEmpty()) {
            showError("Pre-requisite missing", "Please load courses first.");
            return;
        }
        File file = chooseFile("Load Attendance CSV");
        if (file != null) {
            try {
                // Clear previous style classes
                lblAttendanceStatus.getStyleClass().removeAll("text-success", "text-warning", "text-error");

                enrollments = dataImportService.loadAttendance(file, courses, students);
                repository.saveEnrollments(enrollments);

                if (enrollments.isEmpty()) {
                    lblAttendanceStatus.setText("No enrollments found in file");
                    lblAttendanceStatus.getStyleClass().add("text-warning");
                    showWarning("Empty Import", "No valid enrollments found in the file.");
                } else {
                    lblAttendanceStatus.setText(file.getName() + " • " + enrollments.size() + " enrollments loaded");
                    lblAttendanceStatus.getStyleClass().add("text-success");
                }
            } catch (IllegalArgumentException e) {
                showWarning("Import Error", e.getMessage());
                lblAttendanceStatus.setText("Import failed");
                lblAttendanceStatus.getStyleClass().add("text-warning");
            } catch (com.examplanner.persistence.DataAccessException e) {
                showError("Database Error", "Failed to save enrollments to database:\n" + e.getMessage());
                lblAttendanceStatus.setText("Database error");
                lblAttendanceStatus.getStyleClass().add("text-error");
            } catch (Exception e) {
                showError("Error loading attendance",
                        "Your file may be empty or formatted incorrectly.\nError: " + e.getMessage());
                lblAttendanceStatus.setText("Import failed");
                lblAttendanceStatus.getStyleClass().add("text-error");
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleGenerateTimetable() {
        System.out.println("=== GENERATE TIMETABLE BUTTON CLICKED ===");

        if (courses.isEmpty() || classrooms.isEmpty() || enrollments.isEmpty()) {
            System.out.println("ERROR: Missing data!");
            System.out.println("Courses: " + courses.size());
            System.out.println("Classrooms: " + classrooms.size());
            System.out.println("Enrollments: " + enrollments.size());
            showError("Missing Data", "Please load Courses, Classrooms, and Attendance data first.");
            return;
        }

        // Create date picker dialog
        Dialog<javafx.util.Pair<LocalDate, LocalDate>> dialog = new Dialog<>();
        dialog.setTitle("Select Exam Period");
        dialog.setHeaderText("Choose exam period start and end dates");

        // Set button types
        ButtonType generateButtonType = new ButtonType("Generate", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(generateButtonType, ButtonType.CANCEL);

        // Create date pickers
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        javafx.scene.control.DatePicker startDatePicker = new javafx.scene.control.DatePicker(
                LocalDate.now().plusDays(1));
        javafx.scene.control.DatePicker endDatePicker = new javafx.scene.control.DatePicker(
                LocalDate.now().plusDays(7));

        grid.add(new Label("Start Date:"), 0, 0);
        grid.add(startDatePicker, 1, 0);
        grid.add(new Label("End Date:"), 0, 1);
        grid.add(endDatePicker, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == generateButtonType) {
                LocalDate start = startDatePicker.getValue();
                LocalDate end = endDatePicker.getValue();
                if (start != null && end != null && !end.isBefore(start)) {
                    return new javafx.util.Pair<>(start, end);
                }
            }
            return null;
        });

        java.util.Optional<javafx.util.Pair<LocalDate, LocalDate>> result = dialog.showAndWait();

        if (!result.isPresent()) {
            return; // User cancelled
        }

        LocalDate startDate = result.get().getKey();
        LocalDate endDate = result.get().getValue();

        System.out.println("Selected date range: " + startDate + " to " + endDate);

        setLoadingState(true);

        Task<ExamTimetable> task = new Task<>() {
            @Override
            protected ExamTimetable call() throws Exception {
                System.out.println("Starting timetable generation...");
                System.out.println("Start date: " + startDate);
                System.out.println("End date: " + endDate);
                System.out.println("Calling scheduler service...");
                return schedulerService.generateTimetable(courses, classrooms, enrollments, startDate, endDate);
            }
        };

        task.setOnSucceeded(e -> {
            try {
                this.currentTimetable = task.getValue();
                repository.saveTimetable(currentTimetable);
                System.out.println("Timetable generated successfully!");
                System.out.println(
                        "Number of exams: " + (currentTimetable != null ? currentTimetable.getExams().size() : "null"));

                refreshTimetable();
                showTimetable();
                setLoadingState(false);
                System.out.println("UI updated successfully!");
            } catch (Exception ex) {
                System.err.println("CRITICAL UI ERROR:");
                ex.printStackTrace();
                showError("UI Error", "Failed to render timetable: " + ex.getMessage());
                setLoadingState(false);
            }
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("ERROR during timetable generation:");
            ex.printStackTrace();
            showError("Scheduling Failed",
                    "Could not generate timetable.\n\nError: " + ex.getMessage() + "\n\nCheck terminal for details.");
            setLoadingState(false);
        });

        new Thread(task).start();
    }

    @FXML
    private void handleDeleteData() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete All Data");
        alert.setHeaderText("Warning: This action cannot be undone!");
        alert.setContentText(
                "Are you sure you want to permanently delete ALL courses, students, classrooms, exams, and enrollments?");

        if (alert.showAndWait().get() == javafx.scene.control.ButtonType.OK) {
            repository.clearAllData();

            courses.clear();
            classrooms.clear();
            students.clear();
            enrollments.clear();

            currentTimetable = null;

            lblCoursesStatus.setText("Cleared");
            lblCoursesStatus.getStyleClass().removeAll("text-success", "text-error");

            lblClassroomsStatus.setText("Cleared");
            lblClassroomsStatus.getStyleClass().removeAll("text-success", "text-error");

            lblStudentsStatus.setText("Cleared");
            lblStudentsStatus.getStyleClass().removeAll("text-success", "text-error");

            lblAttendanceStatus.setText("Cleared");
            lblAttendanceStatus.getStyleClass().removeAll("text-success", "text-error");

            refreshTimetable();
            refreshDashboard();

            showInformation("Success", "All data has been deleted.");
        }
    }

    private void setLoadingState(boolean loading) {
        String text = loading ? "Generating..." : "Generate Timetable";

        if (btnGenerateDataImport != null) {
            btnGenerateDataImport.setDisable(loading);
            btnGenerateDataImport.setText(text);
        }
        if (btnGenerateTimetable != null) {
            btnGenerateTimetable.setDisable(loading);
            btnGenerateTimetable.setText(text);
        }

        // Show/hide progress bar
        if (progressContainer != null) {
            progressContainer.setVisible(loading);
            progressContainer.setManaged(loading);
        }
        if (progressBar != null) {
            progressBar.setProgress(loading ? -1 : 0); // -1 for indeterminate progress
        }
        if (lblProgressStatus != null) {
            lblProgressStatus.setText("Generating timetable... Please wait.");
        }

        if (viewDataImport.getScene() != null) {
            viewDataImport.getScene().setCursor(loading ? javafx.scene.Cursor.WAIT : javafx.scene.Cursor.DEFAULT);
        }
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }

    @FXML
    private void handleFilter() {
        if (currentTimetable == null) {
            showError("No Timetable", "Please generate a timetable first.");
            return;
        }

        // Directly show student filter (Course filter is now in the table search bar)
        filterByStudent();
    }

    private void filterByStudent() {
        List<Student> studentList = enrollments.stream()
                .map(Enrollment::getStudent)
                .distinct()
                .sorted(Comparator.comparing(Student::getName))
                .collect(Collectors.toList());

        SearchableDialog<Student> dialog = new SearchableDialog<>(
                "Select Student",
                "Filter by Student",
                studentList,
                (student, query) -> student.getName().toLowerCase().contains(query)
                        || student.getId().toLowerCase().contains(query));

        dialog.showAndWait().ifPresent(student -> {
            showStudentTimetable(student);
        });
    }

    private void showStudentTimetable(Student student) {
        if (currentTimetable == null) {
            showError("No Timetable", "Please generate a timetable first.");
            return;
        }

        List<Exam> exams = currentTimetable.getExamsForStudent(student);
        showFilteredExams("Timetable for " + student.getName(), exams);
    }

    private void filterByCourse() {
        List<Course> courseList = new ArrayList<>(courses);
        courseList.sort(Comparator.comparing(Course::getName));

        SearchableDialog<Course> dialog = new SearchableDialog<>(
                "Select Course",
                "Filter by Course",
                courseList,
                (course, query) -> course.getName().toLowerCase().contains(query)
                        || course.getCode().toLowerCase().contains(query));

        dialog.showAndWait().ifPresent(course -> {
            List<Exam> exams = currentTimetable.getExamsForCourse(course);
            showFilteredExams("Exams for " + course.getName(), exams);
        });
    }

    @FXML
    private void handleExport() {
        if (currentTimetable == null || currentTimetable.getExams().isEmpty()) {
            showError("No Timetable", "Nothing to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Timetable to CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("timetable_export.csv");
        File file = fileChooser.showSaveDialog(btnTimetable.getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("Date,Time,Course Code,Course Name,Classroom");
                List<Exam> sortedExams = new ArrayList<>(currentTimetable.getExams());
                sortedExams.sort(Comparator.comparing((Exam e) -> e.getSlot().getDate())
                        .thenComparing(e -> e.getSlot().getStartTime()));

                for (Exam e : sortedExams) {
                    writer.printf("%s,%s,%s,%s,%s%n",
                            e.getSlot().getDate(),
                            e.getSlot().getStartTime(),
                            e.getCourse().getCode(),
                            e.getCourse().getName(),
                            e.getClassroom().getName());
                }

                showInformation("Export Successful", "Timetable exported to " + file.getName());
            } catch (Exception e) {
                e.printStackTrace();
                showError("Export Failed", "Could not save file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportPdf() {
        if (currentTimetable == null || currentTimetable.getExams().isEmpty()) {
            showError("No Timetable", "Nothing to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Timetable to PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("timetable_export.pdf");
        File file = fileChooser.showSaveDialog(btnTimetable.getScene().getWindow());

        if (file != null) {
            try {
                exportToPdf(file);
                showInformation("Export Successful", "Timetable exported to " + file.getName());
            } catch (Exception e) {
                e.printStackTrace();
                showError("Export Failed", "Could not save PDF: " + e.getMessage());
            }
        }
    }

    private void exportToPdf(File file) throws Exception {
        com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(file);
        com.itextpdf.kernel.pdf.PdfDocument pdf = new com.itextpdf.kernel.pdf.PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf,
                com.itextpdf.kernel.geom.PageSize.A4);
        document.setMargins(40, 40, 40, 40);

        // Title
        com.itextpdf.layout.element.Paragraph title = new com.itextpdf.layout.element.Paragraph("Exam Timetable")
                .setFontSize(24)
                .setBold()
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMarginBottom(10);
        document.add(title);

        // Subtitle
        com.itextpdf.layout.element.Paragraph subtitle = new com.itextpdf.layout.element.Paragraph(
                "Generated on " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")))
                .setFontSize(12)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(subtitle);

        // Create table
        float[] columnWidths = { 100f, 80f, 80f, 150f, 80f };
        com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(columnWidths);
        table.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));

        // Header cells with styling
        com.itextpdf.kernel.colors.Color headerColor = new com.itextpdf.kernel.colors.DeviceRgb(139, 92, 246);
        String[] headers = { "Date", "Start", "End", "Course", "Room" };
        for (String header : headers) {
            com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                    .add(new com.itextpdf.layout.element.Paragraph(header).setBold())
                    .setBackgroundColor(headerColor)
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
                    .setPadding(8)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
            table.addHeaderCell(cell);
        }

        // Sort exams
        List<Exam> sortedExams = new ArrayList<>(currentTimetable.getExams());
        sortedExams.sort(Comparator.comparing((Exam e) -> e.getSlot().getDate())
                .thenComparing(e -> e.getSlot().getStartTime()));

        // Data rows
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        com.itextpdf.kernel.colors.Color altRowColor = new com.itextpdf.kernel.colors.DeviceRgb(249, 250, 251);

        int rowIndex = 0;
        for (Exam exam : sortedExams) {
            com.itextpdf.kernel.colors.Color rowColor = (rowIndex % 2 == 0)
                    ? com.itextpdf.kernel.colors.ColorConstants.WHITE
                    : altRowColor;

            table.addCell(createCell(exam.getSlot().getDate().format(dateFmt), rowColor));
            table.addCell(createCell(exam.getSlot().getStartTime().format(timeFmt), rowColor));
            table.addCell(createCell(exam.getSlot().getEndTime().format(timeFmt), rowColor));
            table.addCell(createCell(exam.getCourse().getCode() + " - " + exam.getCourse().getName(), rowColor));
            table.addCell(createCell(exam.getClassroom().getName(), rowColor));
            rowIndex++;
        }

        document.add(table);

        // Footer
        com.itextpdf.layout.element.Paragraph footer = new com.itextpdf.layout.element.Paragraph(
                "Total Exams: " + sortedExams.size())
                .setFontSize(10)
                .setMarginTop(20)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT);
        document.add(footer);

        document.close();
    }

    private com.itextpdf.layout.element.Cell createCell(String content, com.itextpdf.kernel.colors.Color bgColor) {
        return new com.itextpdf.layout.element.Cell()
                .add(new com.itextpdf.layout.element.Paragraph(content).setFontSize(10))
                .setBackgroundColor(bgColor)
                .setPadding(6)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.LEFT);
    }

    @FXML
    private void handleConflicts() {
        if (currentTimetable == null) {
            showError("No Timetable", "Please generate a timetable first.");
            return;
        }

        // Detect "Conflicts" -> Students having > 1 exam per day (Soft constraint
        // warning)
        // Since hard Constraint is max 2 exams/day, a "conflict" here could mean 2
        // exams on the same day
        // to warn the user about heavy load, OR checking for < 30 min gap if we allowed
        // it (but we didn't).

        // Group exams by student -> date
        // Note: This is computationally expensive O(N_students * M_exams), but for the
        // UI button it's okay?
        // Better: Iterate exams, and for each exam find enrolled students.

        Map<Student, Map<LocalDate, List<Exam>>> studentDailyLoad = new HashMap<>();

        for (Exam exam : currentTimetable.getExams()) {
            List<Student> enrolled = enrollments.stream()
                    .filter(e -> e.getCourse().getCode().equals(exam.getCourse().getCode()))
                    .map(Enrollment::getStudent)
                    .collect(Collectors.toList());

            for (Student s : enrolled) {
                studentDailyLoad.putIfAbsent(s, new HashMap<>());
                Map<LocalDate, List<Exam>> days = studentDailyLoad.get(s);
                LocalDate d = exam.getSlot().getDate();
                days.putIfAbsent(d, new ArrayList<>());
                days.get(d).add(exam);
            }
        }

        // Filter those with > 1 exam
        List<String> reportLines = new ArrayList<>();

        for (Map.Entry<Student, Map<LocalDate, List<Exam>>> entry : studentDailyLoad.entrySet()) {
            Student s = entry.getKey();
            for (Map.Entry<LocalDate, List<Exam>> dayEntry : entry.getValue().entrySet()) {
                if (dayEntry.getValue().size() > 1) {
                    reportLines.add("🔴 " + s.getName() + " has " + dayEntry.getValue().size() + " exams on "
                            + dayEntry.getKey());
                    // Maybe list the exams?
                    reportLines.add("   " + dayEntry.getValue().stream().map(e -> e.getCourse().getCode())
                            .collect(Collectors.joining(", ")));
                }
            }
        }

        if (reportLines.isEmpty()) {
            showInformation("No Conflicts", "No students have more than 1 exam per day.");
        } else {
            showScrollableDialog("Exam Load Conflicts", reportLines);
        }
    }

    @FXML
    private void handleStudentPortal() {
        if (students.isEmpty()) {
            showError("No Data", "Please load students first.");
            return;
        }

        // Simulating a "Login"
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Student Portal Login");
        dialog.setHeaderText("Welcome Student");
        dialog.setContentText("Please enter your Student ID:");

        dialog.showAndWait().ifPresent(id -> {
            Student found = students.stream()
                    .filter(s -> s.getId().equalsIgnoreCase(id.trim()))
                    .findFirst()
                    .orElse(null);

            if (found != null) {
                if (currentTimetable != null) {
                    List<Exam> exams = currentTimetable.getExamsForStudent(found);
                    showFilteredExams("Welcome, " + found.getName(), exams);
                } else {
                    // Even if no timetable, show we found the student but no exams yet
                    showInformation("Welcome " + found.getName(), "No timetable generated yet.");
                }
            } else {
                showError("Login Failed", "Student ID not found: " + id);
            }
        });
    }

    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInformation(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showScrollableDialog(String title, List<String> lines) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox root = new VBox(10);
        root.setPadding(new javafx.geometry.Insets(20));

        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        ListView<String> list = new ListView<>();
        list.getItems().addAll(lines);

        Button close = new Button("Close");
        close.setOnAction(e -> dialog.close());

        root.getChildren().addAll(lblTitle, list, close);
        Scene scene = new Scene(root, 500, 600);

        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showFilteredExams(String titleText, List<Exam> exams) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox();
        root.getStyleClass().add("modal-window");
        root.setPrefSize(900, 600);

        // Header
        VBox header = new VBox(5);
        header.getStyleClass().add("modal-header");

        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        title.setStyle("-fx-font-size: 16px;");

        Label subtitle = new Label(exams.size() + " exams scheduled");
        subtitle.getStyleClass().addAll("label", "text-secondary");

        header.getChildren().addAll(title, subtitle);

        // Content - Grid/Calendar View
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        if (exams.isEmpty()) {
            VBox emptyContent = new VBox();
            emptyContent.setAlignment(javafx.geometry.Pos.CENTER);
            emptyContent.setPadding(new javafx.geometry.Insets(50));
            Label empty = new Label("No exams found for this selection.");
            empty.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 14px;");
            emptyContent.getChildren().add(empty);
            scrollPane.setContent(emptyContent);
        } else {
            // Build Grid Calendar
            javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
            grid.setHgap(5);
            grid.setVgap(0); // No vertical gap - lines will align with rows
            grid.setPadding(new javafx.geometry.Insets(15));
            grid.setStyle("-fx-background-color: white;");

            // Constants
            double SLOT_HEIGHT = 50.0;
            double PX_PER_HALF_HOUR = SLOT_HEIGHT; // No gap, just slot height
            int START_HOUR = 9;
            int END_HOUR = 18;
            int TOTAL_SLOTS = (END_HOUR - START_HOUR) * 2 + 1;

            // Row constraints
            javafx.scene.layout.RowConstraints headerRow = new javafx.scene.layout.RowConstraints();
            headerRow.setMinHeight(40);
            headerRow.setPrefHeight(40);
            headerRow.setMaxHeight(40);
            headerRow.setVgrow(javafx.scene.layout.Priority.NEVER);
            grid.getRowConstraints().add(headerRow);

            for (int i = 0; i < TOTAL_SLOTS; i++) {
                javafx.scene.layout.RowConstraints row = new javafx.scene.layout.RowConstraints();
                row.setMinHeight(SLOT_HEIGHT);
                row.setPrefHeight(SLOT_HEIGHT);
                row.setMaxHeight(SLOT_HEIGHT);
                row.setVgrow(javafx.scene.layout.Priority.NEVER);
                grid.getRowConstraints().add(row);
            }

            // Column constraints - Time column
            javafx.scene.layout.ColumnConstraints timeCol = new javafx.scene.layout.ColumnConstraints();
            timeCol.setMinWidth(60);
            timeCol.setPrefWidth(60);
            grid.getColumnConstraints().add(timeCol);

            // Find start date from all exams in timetable and create 7-day week
            LocalDate startDate = currentTimetable != null && !currentTimetable.getExams().isEmpty()
                    ? currentTimetable.getExams().stream()
                            .map(e -> e.getSlot().getDate())
                            .min(LocalDate::compareTo)
                            .orElse(LocalDate.now())
                    : LocalDate.now();

            List<LocalDate> weekDates = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                weekDates.add(startDate.plusDays(i));
            }

            // Add column constraints for each day (7 days)
            for (int i = 0; i < weekDates.size(); i++) {
                javafx.scene.layout.ColumnConstraints dayCol = new javafx.scene.layout.ColumnConstraints();
                dayCol.setMinWidth(100);
                dayCol.setPrefWidth(110);
                dayCol.setHgrow(javafx.scene.layout.Priority.ALWAYS);
                grid.getColumnConstraints().add(dayCol);
            }

            // Time Labels
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            int row = 1;
            for (int hour = START_HOUR; hour <= END_HOUR; hour++) {
                for (int min = 0; min < 60; min += 30) {
                    if (hour == END_HOUR && min > 0)
                        break;

                    String timeStr = java.time.LocalTime.of(hour, min).format(timeFormatter);
                    Label timeLabel = new Label(timeStr);
                    timeLabel.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 11px;");
                    javafx.scene.layout.GridPane.setValignment(timeLabel, javafx.geometry.VPos.TOP);
                    javafx.scene.layout.GridPane.setHalignment(timeLabel, javafx.geometry.HPos.RIGHT);
                    grid.add(timeLabel, 0, row);
                    row++;
                }
            }

            // Day Headers
            DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("EEE");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d");

            for (int i = 0; i < weekDates.size(); i++) {
                LocalDate date = weekDates.get(i);
                VBox dayHeader = new VBox();
                dayHeader.setAlignment(javafx.geometry.Pos.CENTER);
                dayHeader.setStyle("-fx-background-color: #F3F4F6; -fx-padding: 8; -fx-background-radius: 6;");

                Label dayLabel = new Label(date.format(dayFormatter));
                dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #374151;");
                Label dateLabel = new Label(date.format(dateFormatter));
                dateLabel.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 11px;");

                dayHeader.getChildren().addAll(dayLabel, dateLabel);
                grid.add(dayHeader, i + 1, 0);
            }

            // Place exam cards
            String[] colors = { "purple", "orange", "pink", "blue", "green", "red" };
            int colorIdx = 0;

            for (int dayIdx = 0; dayIdx < weekDates.size(); dayIdx++) {
                LocalDate day = weekDates.get(dayIdx);
                int colIndex = dayIdx + 1;

                // Create AnchorPane for this day
                javafx.scene.layout.AnchorPane dayPane = new javafx.scene.layout.AnchorPane();
                javafx.scene.layout.GridPane.setMargin(dayPane, new javafx.geometry.Insets(10, 0, 0, 0));
                grid.add(dayPane, colIndex, 1, 1, TOTAL_SLOTS);

                // Add time guide lines
                for (int i = 0; i <= TOTAL_SLOTS; i++) {
                    double y = i * PX_PER_HALF_HOUR;
                    javafx.scene.shape.Line line = new javafx.scene.shape.Line();
                    line.setStartX(0);
                    line.setStartY(y);
                    line.endXProperty().bind(dayPane.widthProperty());
                    line.setEndY(y);
                    line.setMouseTransparent(true);
                    boolean isHourLine = (i % 2 == 0);
                    line.setStroke(javafx.scene.paint.Color.web("#E5E7EB", isHourLine ? 0.9 : 0.55));
                    line.setStrokeWidth(isHourLine ? 1.0 : 0.75);
                    dayPane.getChildren().add(line);
                }

                // Get exams for this day
                List<Exam> dayExams = exams.stream()
                        .filter(e -> e.getSlot().getDate().equals(day))
                        .sorted(Comparator.comparing(e -> e.getSlot().getStartTime()))
                        .collect(Collectors.toList());

                // Place exam cards
                for (Exam exam : dayExams) {
                    java.time.LocalTime start = exam.getSlot().getStartTime();
                    int minutesFromStart = (start.getHour() - START_HOUR) * 60 + start.getMinute();
                    double top = (minutesFromStart / 30.0) * PX_PER_HALF_HOUR;
                    double height = (exam.getCourse().getExamDurationMinutes() / 30.0) * PX_PER_HALF_HOUR;

                    VBox card = new VBox(2);
                    String colorClass = "exam-card-" + colors[colorIdx % colors.length];
                    colorIdx++;
                    card.getStyleClass().addAll("exam-card", colorClass);
                    card.setStyle("-fx-padding: 8;");

                    Label courseLabel = new Label(exam.getCourse().getCode());
                    courseLabel.getStyleClass().add("exam-card-title");
                    courseLabel.setStyle("-fx-font-size: 12px;");

                    Label roomLabel = new Label("📍 " + exam.getClassroom().getName());
                    roomLabel.getStyleClass().add("exam-card-detail");
                    roomLabel.setStyle("-fx-font-size: 10px;");

                    card.getChildren().addAll(courseLabel, roomLabel);

                    dayPane.getChildren().add(card);
                    javafx.scene.layout.AnchorPane.setTopAnchor(card, top);
                    javafx.scene.layout.AnchorPane.setLeftAnchor(card, 2.0);
                    javafx.scene.layout.AnchorPane.setRightAnchor(card, 2.0);
                    card.setMinHeight(height - 4);
                    card.setPrefHeight(height - 4);
                    card.setMaxHeight(height - 4);
                }
            }

            scrollPane.setContent(grid);
        }

        javafx.scene.layout.VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        // Footer
        HBox footer = new HBox();
        footer.setStyle(
                "-fx-padding: 15; -fx-alignment: center-right; -fx-background-color: #F3F4F6; -fx-background-radius: 0 0 12 12;");

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> dialog.close());
        footer.getChildren().add(closeBtn);

        root.getChildren().addAll(header, scrollPane, footer);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/examplanner/ui/style.css").toExternalForm());

        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // Helper for separator
    private void parentSeparator(VBox container) {
        javafx.scene.shape.Line line = new javafx.scene.shape.Line(0, 0, 380, 0);
        line.setStroke(javafx.scene.paint.Color.web("#E5E7EB"));
        line.setStrokeWidth(1);
        container.getChildren().add(line);
    }

    private void refreshTimetable() {
        try {
            if (currentTimetable == null || currentTimetable.getExams().isEmpty()) {
                examTableView.setItems(FXCollections.observableArrayList());
                examTableView.setPlaceholder(new Label("No exams scheduled. Click 'Generate Timetable' to begin."));
                return;
            }

            // Setup columns
            setupTableColumns();

            // Sort exams by date then time
            List<Exam> sortedExams = new ArrayList<>(currentTimetable.getExams());
            sortedExams.sort(Comparator.comparing((Exam e) -> e.getSlot().getDate())
                    .thenComparing(e -> e.getSlot().getStartTime()));

            // Set data
            ObservableList<Exam> examData = FXCollections.observableArrayList(sortedExams);
            examTableView.setItems(examData);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Rendering Error", "Failed to refresh timetable: " + e.getMessage());
        }
    }

    private void setupTableColumns() {
        // Sütunların tablonun tüm genişliğini kaplaması için
        examTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // Find the start date for day numbering
        LocalDate startDate = currentTimetable.getExams().stream()
                .map(e -> e.getSlot().getDate())
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        // Exam ID column
        colExamId.setCellValueFactory(cellData -> {
            int index = examTableView.getItems().indexOf(cellData.getValue()) + 1;
            return new SimpleStringProperty(String.format("EX%03d", index));
        });

        // Course Code column
        colCourseCode
                .setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCourse().getCode()));

        // Date column (shows actual date, not day number)
        colDay.setCellValueFactory(cellData -> {
            LocalDate examDate = cellData.getValue().getSlot().getDate();
            return new SimpleStringProperty(examDate.format(dateFmt));
        });

        // Time Slot column
        colTimeSlot.setCellValueFactory(cellData -> {
            Exam exam = cellData.getValue();
            String timeSlot = exam.getSlot().getStartTime().format(timeFmt) + "-" +
                    exam.getSlot().getEndTime().format(timeFmt);
            return new SimpleStringProperty(timeSlot);
        });

        // Classroom column
        colClassroom.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getClassroom().getName()));

        // Students column
        colStudents.setCellValueFactory(cellData -> {
            String courseCode = cellData.getValue().getCourse().getCode();
            long studentCount = enrollments.stream()
                    .filter(e -> e.getCourse().getCode().equals(courseCode))
                    .count();
            return new SimpleIntegerProperty((int) studentCount).asObject();
        });

        // Actions column with Edit button
        colActions.setCellFactory(param -> new TableCell<Exam, Void>() {
            private final Button editBtn = new Button("Edit");
            {
                editBtn.setStyle(
                        "-fx-background-color: transparent; -fx-text-fill: #8B5CF6; -fx-cursor: hand; " +
                                "-fx-border-color: #8B5CF6; -fx-border-radius: 4; -fx-background-radius: 4; " +
                                "-fx-font-size: 14px; -fx-padding: 4 10;");
                editBtn.setOnMouseEntered(e -> editBtn.setStyle(
                        "-fx-background-color: #8B5CF6; -fx-text-fill: white; -fx-cursor: hand; " +
                                "-fx-border-color: #8B5CF6; -fx-border-radius: 4; -fx-background-radius: 4; " +
                                "-fx-font-size: 14px; -fx-padding: 4 10;"));
                editBtn.setOnMouseExited(e -> editBtn.setStyle(
                        "-fx-background-color: transparent; -fx-text-fill: #8B5CF6; -fx-cursor: hand; " +
                                "-fx-border-color: #8B5CF6; -fx-border-radius: 4; -fx-background-radius: 4; " +
                                "-fx-font-size: 14px; -fx-padding: 4 10;"));
                editBtn.setOnAction(event -> {
                    Exam exam = getTableView().getItems().get(getIndex());
                    showExamDetails(exam);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(editBtn);
                }
            }
        });
    }

    // renderTimetableGrid, addTimeGuides, and arrangeDayExams methods removed (now
    // using TableView)

    private void refreshDashboard() {
        if (currentTimetable == null) {
            return;
        }

        // 1. Exams Per Day (Bar Chart)
        chartExamsPerDay.getData().clear();
        chartExamsPerDay.setAnimated(false);

        if (chartExamsPerDay.getXAxis() instanceof javafx.scene.chart.CategoryAxis) {
            javafx.scene.chart.CategoryAxis xAxis = (javafx.scene.chart.CategoryAxis) chartExamsPerDay.getXAxis();
            xAxis.setTickLabelRotation(90);
            xAxis.setTickLabelGap(10);
        }

        Map<LocalDate, Long> examsByDate = currentTimetable.getExams().stream()
                .collect(Collectors.groupingBy(e -> e.getSlot().getDate(), Collectors.counting()));

        javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
        series.setName("Exams");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        // Sort by date
        examsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    series.getData()
                            .add(new javafx.scene.chart.XYChart.Data<>(entry.getKey().format(fmt), entry.getValue()));
                });

        chartExamsPerDay.getData().add(series);

        // 2. Room Utilization (Pie Chart)
        chartRoomUsage.getData().clear();
        Map<String, Long> roomUsage = currentTimetable.getExams().stream()
                .collect(Collectors.groupingBy(e -> e.getClassroom().getName(), Collectors.counting()));

        roomUsage.forEach((room, count) -> {
            chartRoomUsage.getData().add(new javafx.scene.chart.PieChart.Data(room, count));
        });
    }

    private void showExamDetails(Exam exam) {
        List<Student> students = enrollments.stream()
                .filter(e -> e.getCourse().getCode().equals(exam.getCourse().getCode()))
                .map(Enrollment::getStudent)
                .sorted(Comparator.comparing(Student::getName))
                .collect(Collectors.toList());

        // Create Custom Dialog Stage
        // Create Custom Dialog Stage
        Stage dialog = new Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);

        // Root Container
        VBox root = new VBox();
        root.getStyleClass().add("modal-window");
        root.setPrefSize(400, 500);

        // Header
        VBox header = new VBox(5);
        header.getStyleClass().add("modal-header");

        Label title = new Label(exam.getCourse().getName());
        title.getStyleClass().add("section-title");
        title.setStyle("-fx-font-size: 16px;");

        Label subtitle = new Label(exam.getCourse().getCode() + " • " + exam.getClassroom().getName());
        subtitle.getStyleClass().addAll("label", "text-secondary");

        HBox metaBox = new HBox(10);
        Label timeLabel = new Label("🕒 " + exam.getSlot().getStartTime() + " - " + exam.getSlot().getEndTime());
        timeLabel.getStyleClass().addAll("label", "text-secondary");
        Label countLabel = new Label("👥 " + students.size() + " Students");
        countLabel.getStyleClass().addAll("label", "text-secondary");
        metaBox.getChildren().addAll(timeLabel, countLabel);

        header.getChildren().addAll(title, subtitle, metaBox);

        // Content (List)
        VBox content = new VBox();
        content.getStyleClass().add("modal-content");
        javafx.scene.layout.VBox.setVgrow(content, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.control.ListView<Student> listView = new javafx.scene.control.ListView<>();
        listView.getStyleClass().add("student-list-view");
        listView.getItems().addAll(students);
        listView.setCellFactory(param -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Student item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName() + " (" + item.getId() + ")");
                    getStyleClass().add("student-list-cell");
                }
            }
        });

        javafx.scene.layout.VBox.setVgrow(listView, javafx.scene.layout.Priority.ALWAYS);
        content.getChildren().add(listView);

        // Footer (Close Button)
        HBox footer = new HBox();
        footer.setStyle(
                "-fx-padding: 15; -fx-alignment: center-right; -fx-background-color: #F3F4F6; -fx-background-radius: 0 0 12 12;");

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> dialog.close());
        footer.getChildren().add(closeBtn);

        root.getChildren().addAll(header, content, footer);

        // Scene
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/examplanner/ui/style.css").toExternalForm());

        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private File chooseFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        return fileChooser.showOpenDialog(btnDataImport.getScene().getWindow());
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
