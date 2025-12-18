package com.examplanner.ui;

import com.examplanner.domain.Classroom;
import com.examplanner.domain.Course;
import com.examplanner.domain.Enrollment;
import com.examplanner.domain.Exam;
import com.examplanner.domain.ExamSlot;
import com.examplanner.domain.Student;
import com.examplanner.domain.ExamTimetable;
import com.examplanner.services.DataImportService;
import com.examplanner.services.SchedulerService;
import javafx.concurrent.Task;
import java.time.LocalTime;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
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
import java.util.ResourceBundle;
import java.util.Locale;

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
    private Button btnSettings;

    @FXML
    private Button btnGenerateDataImport;
    @FXML
    private Button btnGenerateTimetable;
    @FXML
    private Button btnDeleteData;
    @FXML
    private Button btnValidateAll;

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

    // FXML fields for I18N
    @FXML
    private Button btnExit;
    @FXML
    private Label lblDataImportTitle;
    @FXML
    private Label lblDataImportSubtitle;
    @FXML
    private Label lblCoursesTitle;
    @FXML
    private Label lblStudentsTitle;
    @FXML
    private Label lblClassroomsTitle;
    @FXML
    private Label lblAttendanceTitle;

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
    private javafx.scene.control.ComboBox<String> cmbSearchType;

    @FXML
    private Button btnClearSearch;

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

    // Edit history tracking
    private List<EditHistoryEntry> editHistory = new ArrayList<>();

    // Inner class for edit history entry
    private static class EditHistoryEntry {
        private final String timestamp;
        private final String courseCode;
        private final String courseName;
        private final String changeDescription;
        private final String oldValue;
        private final String newValue;

        public EditHistoryEntry(String courseCode, String courseName, String changeDescription, String oldValue,
                String newValue) {
            this.timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.changeDescription = changeDescription;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getCourseCode() {
            return courseCode;
        }

        public String getCourseName() {
            return courseName;
        }

        public String getChangeDescription() {
            return changeDescription;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }
    }

    private DataImportService dataImportService = new DataImportService();
    private SchedulerService schedulerService = new SchedulerService();
    private com.examplanner.persistence.DataRepository repository = new com.examplanner.persistence.DataRepository();
    private com.examplanner.services.ConstraintChecker constraintChecker = new com.examplanner.services.ConstraintChecker();

    @FXML
    public void initialize() {
        // Load Theme Preference
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        isDarkMode = prefs.getBoolean("theme_preference", false);
        Platform.runLater(this::applyTheme);

        // Load default language (English)
        loadLanguage("en");
        constraintChecker.setMinGapMinutes(180); // Default to requirements
        showDataImport();

        // Setup advanced search
        setupAdvancedSearch();

        // Load data from DB
        List<Course> loadedCourses = repository.loadCourses();
        if (!loadedCourses.isEmpty()) {
            this.courses = loadedCourses;
            lblCoursesStatus.setText("Loaded from DB (" + courses.size() + ")");
            lblCoursesStatus.setText("Loaded from DB (" + courses.size() + ")");
            lblCoursesStatus.getStyleClass().removeAll("text-success", "text-warning", "text-error");
            lblCoursesStatus.getStyleClass().add("text-success");
        }

        List<Classroom> loadedClassrooms = repository.loadClassrooms();
        if (!loadedClassrooms.isEmpty()) {
            this.classrooms = loadedClassrooms;
            lblClassroomsStatus.setText("Loaded from DB (" + classrooms.size() + ")");
            lblClassroomsStatus.setText("Loaded from DB (" + classrooms.size() + ")");
            lblClassroomsStatus.getStyleClass().removeAll("text-success", "text-warning", "text-error");
            lblClassroomsStatus.getStyleClass().add("text-success");
        }

        List<Student> loadedStudents = repository.loadStudents();
        if (!loadedStudents.isEmpty()) {
            this.students = loadedStudents;
            lblStudentsStatus.setText("Loaded from DB (" + students.size() + ")");
            lblStudentsStatus.setText("Loaded from DB (" + students.size() + ")");
            lblStudentsStatus.getStyleClass().removeAll("text-success", "text-warning", "text-error");
            lblStudentsStatus.getStyleClass().add("text-success");
        }

        // Enrollments depend on students and courses
        if (!students.isEmpty() && !courses.isEmpty()) {
            List<Enrollment> loadedEnrollments = repository.loadEnrollments(students, courses);
            if (!loadedEnrollments.isEmpty()) {
                this.enrollments = loadedEnrollments;
                lblAttendanceStatus.setText("Loaded from DB (" + enrollments.size() + ")");
                lblAttendanceStatus.getStyleClass().removeAll("text-success", "text-warning", "text-error");
                lblAttendanceStatus.getStyleClass().add("text-success");

                // Load Timetable if everything else is present
                ExamTimetable loadedTimetable = repository.loadTimetable(courses, classrooms, enrollments);
                if (loadedTimetable != null) {
                    this.currentTimetable = loadedTimetable;
                    refreshTimetable();
                }
            }
        }
    }

    private void setupAdvancedSearch() {
        if (cmbSearchType != null) {
            // Populate search type options
            cmbSearchType.getItems().addAll(
                    "Course",
                    "Date",
                    "Time",
                    "Classroom");
            cmbSearchType.setValue("Course");

            // Update placeholder text based on selection
            cmbSearchType.setOnAction(e -> {
                String selected = cmbSearchType.getValue();
                if (selected != null) {
                    if (selected.equals("Course")) {
                        txtCourseSearch.setPromptText("e.g. CourseCode_01");
                    } else if (selected.equals("Date")) {
                        txtCourseSearch.setPromptText("e.g. 18/12/2025");
                    } else if (selected.equals("Time")) {
                        txtCourseSearch.setPromptText("e.g. 10:00");
                    } else if (selected.equals("Classroom")) {
                        txtCourseSearch.setPromptText("e.g. Classroom_01");
                    }
                }
                // Re-apply filter when type changes
                applyAdvancedFilter(txtCourseSearch.getText());
            });
        }

        if (txtCourseSearch != null) {
            txtCourseSearch.textProperty().addListener((observable, oldValue, newValue) -> {
                applyAdvancedFilter(newValue);
            });
        }
    }

    @FXML
    private void handleClearSearch() {
        if (txtCourseSearch != null) {
            txtCourseSearch.clear();
        }
        applyAdvancedFilter("");
    }

    private void applyAdvancedFilter(String searchText) {
        if (currentTimetable == null || currentTimetable.getExams().isEmpty()) {
            return;
        }

        if (searchText == null || searchText.trim().isEmpty()) {
            // Show all exams
            List<Exam> sortedExams = new ArrayList<>(currentTimetable.getExams());
            sortedExams.sort(Comparator.comparing((Exam e) -> e.getSlot().getDate())
                    .thenComparing(e -> e.getSlot().getStartTime()));
            examTableView.setItems(FXCollections.observableArrayList(sortedExams));
            return;
        }

        String searchType = cmbSearchType != null ? cmbSearchType.getValue() : "Course";
        String lowerSearch = searchText.toLowerCase().trim();

        List<Exam> filteredExams;

        if (searchType.equals("Course")) {
            // Filter by course code
            filteredExams = currentTimetable.getExams().stream()
                    .filter(e -> e.getCourse().getCode().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        } else if (searchType.equals("Date")) {
            // Filter by date (supports multiple formats: dd/MM/yyyy, dd.MM.yyyy, dd/MM,
            // dd.MM)
            filteredExams = currentTimetable.getExams().stream()
                    .filter(e -> {
                        LocalDate date = e.getSlot().getDate();
                        String dateStr1 = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        String dateStr2 = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                        String dateStr3 = date.format(DateTimeFormatter.ofPattern("dd/MM"));
                        String dateStr4 = date.format(DateTimeFormatter.ofPattern("dd.MM"));
                        return dateStr1.contains(lowerSearch) || dateStr2.contains(lowerSearch) ||
                                dateStr3.contains(lowerSearch) || dateStr4.contains(lowerSearch);
                    })
                    .collect(Collectors.toList());
        } else if (searchType.equals("Time")) {
            // Filter by time range (supports: HH:mm or HH:mm-HH:mm)
            filteredExams = currentTimetable.getExams().stream()
                    .filter(e -> {
                        String startTime = e.getSlot().getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"));
                        String endTime = e.getSlot().getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));
                        String timeRange = startTime + "-" + endTime;
                        return startTime.contains(lowerSearch) || endTime.contains(lowerSearch) ||
                                timeRange.contains(lowerSearch);
                    })
                    .collect(Collectors.toList());
        } else if (searchType.equals("Classroom")) {
            // Filter by classroom
            filteredExams = currentTimetable.getExams().stream()
                    .filter(e -> e.getClassroom().getName().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        } else {
            // Default: show all
            filteredExams = new ArrayList<>(currentTimetable.getExams());
        }

        // Sort and display
        filteredExams.sort(Comparator.comparing((Exam e) -> e.getSlot().getDate())
                .thenComparing(e -> e.getSlot().getStartTime()));
        examTableView.setItems(FXCollections.observableArrayList(filteredExams));
    }

    private void filterTableByCourseCode(String searchText) {
        // Deprecated - now using applyAdvancedFilter
        applyAdvancedFilter(searchText);
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

                
                java.util.Set<String> existingStudentIds = students.stream()
                        .map(Student::getId)
                        .collect(Collectors.toSet());

                List<Student> newStudents = new ArrayList<>();
                for (Enrollment e : enrollments) {
                    Student s = e.getStudent();
                    if (!existingStudentIds.contains(s.getId())) {
                        existingStudentIds.add(s.getId());
                        newStudents.add(s);
                        students.add(s);
                    }
                }

                // Save any new students to database
                if (!newStudents.isEmpty()) {
                    repository.saveStudents(newStudents);
                    System.out.println("Auto-created " + newStudents.size() + " students from attendance data");
                    // Update students status label
                    lblStudentsStatus.setText("Auto-imported (" + students.size() + " total)");
                    lblStudentsStatus.getStyleClass().removeAll("text-success", "text-warning", "text-error");
                    lblStudentsStatus.getStyleClass().add("text-success");
                }

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
        applyDarkModeToDialogPane(dialog);

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
        applyDarkModeToAlert(alert);

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

    // Track dark mode state
    private boolean isDarkMode = false;
    // Track language: "en" for English, "tr" for Turkish
    private String currentLanguage = "en";
    private ResourceBundle bundle;

    private void loadLanguage(String lang) {
        currentLanguage = lang;
        Locale locale = new Locale(lang);
        bundle = ResourceBundle.getBundle("com.examplanner.ui.messages", locale);
        updateUIText();
    }

    private void updateUIText() {
        if (bundle == null)
            return;

        // Sidebar
        if (btnDataImport != null)
            btnDataImport.setText(bundle.getString("sidebar.dataImport"));
        if (btnDashboard != null)
            btnDashboard.setText(bundle.getString("sidebar.dashboard"));
        if (btnTimetable != null)
            btnTimetable.setText(bundle.getString("sidebar.timetable"));
        if (btnFilter != null)
            btnFilter.setText(bundle.getString("sidebar.studentSearch"));
        if (btnUserManual != null)
            btnUserManual.setText(bundle.getString("sidebar.userManual"));
        if (btnStudentPortal != null)
            btnStudentPortal.setText(bundle.getString("sidebar.studentPortal"));
        if (btnSettings != null)
            btnSettings.setText(bundle.getString("sidebar.settings"));
        if (btnExit != null)
            btnExit.setText(bundle.getString("sidebar.exit"));

        // Data Import View
        if (lblDataImportTitle != null)
            lblDataImportTitle.setText(bundle.getString("dataImport.title"));
        if (lblDataImportSubtitle != null)
            lblDataImportSubtitle.setText(bundle.getString("dataImport.subtitle"));
        if (lblCoursesTitle != null)
            lblCoursesTitle.setText(bundle.getString("dataImport.courses"));
        if (lblStudentsTitle != null)
            lblStudentsTitle.setText(bundle.getString("dataImport.students"));
        if (lblClassroomsTitle != null)
            lblClassroomsTitle.setText(bundle.getString("dataImport.classrooms"));
        if (lblAttendanceTitle != null)
            lblAttendanceTitle.setText(bundle.getString("dataImport.attendance"));

        // Update loaded/not loaded status texts using current file status
        updateImportStatusLabels();

        if (btnGenerateDataImport != null)
            btnGenerateDataImport.setText(bundle.getString("dataImport.generateTimetable"));
        if (btnDeleteData != null)
            btnDeleteData.setText(bundle.getString("dataImport.deleteData"));

        // Timetable View
        if (btnGenerateTimetable != null)
            btnGenerateTimetable.setText(bundle.getString("dataImport.generateTimetable"));
    }

    // Helper to update status labels with translated "Loaded"/"Not Loaded" text
    private void updateImportStatusLabels() {
        if (bundle == null)
            return;
        String loaded = bundle.getString("dataImport.loaded");
        String notLoaded = bundle.getString("dataImport.notLoaded");

        if (lblCoursesStatus != null)
            lblCoursesStatus.setText("courses.csv • " + (repository.loadCourses().isEmpty() ? notLoaded : loaded));
        if (lblStudentsStatus != null)
            lblStudentsStatus.setText("students.csv • " + (repository.loadStudents().isEmpty() ? notLoaded : loaded));
        if (lblClassroomsStatus != null)
            lblClassroomsStatus.setText("rooms.csv • " + (repository.loadClassrooms().isEmpty() ? notLoaded : loaded));
        if (lblAttendanceStatus != null)
            lblAttendanceStatus.setText("attendance.csv • "
                    + (repository.loadEnrollments(students, courses).isEmpty() ? notLoaded : loaded));
    }

    @FXML
    private void handleSettings() {
        if (bundle == null)
            loadLanguage("en"); // Ensure bundle is loaded

        Stage settingsStage = new Stage();
        settingsStage.initModality(Modality.APPLICATION_MODAL);
        settingsStage.initStyle(StageStyle.TRANSPARENT);
        settingsStage.setTitle(bundle.getString("settings.title"));

        VBox settingsContent = new VBox(15);
        settingsContent.getStyleClass().add("settings-popup");
        settingsContent.setStyle("-fx-min-width: 280px; -fx-max-width: 280px;");

        // Title
        Label titleLabel = new Label(bundle.getString("settings.title"));
        titleLabel.getStyleClass().add("settings-title");

        // Theme Section
        Label themeLabel = new Label(bundle.getString("settings.theme"));
        themeLabel.getStyleClass().add("settings-section-title");

        // Light Mode Button
        HBox lightModeBtn = new HBox(12);
        lightModeBtn.getStyleClass().add("settings-option");
        lightModeBtn.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lightIcon = new Label("☀️");
        lightIcon.getStyleClass().add("settings-option-icon");
        Label lightText = new Label(bundle.getString("settings.lightMode"));
        lightText.getStyleClass().add("settings-option-text");
        lightModeBtn.getChildren().addAll(lightIcon, lightText);

        // Dark Mode Button
        HBox darkModeBtn = new HBox(12);
        darkModeBtn.getStyleClass().add("settings-option");
        darkModeBtn.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label darkIcon = new Label("🌙");
        darkIcon.getStyleClass().add("settings-option-icon");
        Label darkText = new Label(bundle.getString("settings.darkMode"));
        darkText.getStyleClass().add("settings-option-text");
        darkModeBtn.getChildren().addAll(darkIcon, darkText);

        if (isDarkMode) {
            darkModeBtn.getStyleClass().add("selected");
        } else {
            lightModeBtn.getStyleClass().add("selected");
        }

        // Theme click handlers
        lightModeBtn.setOnMouseClicked(e -> {
            if (isDarkMode) {
                isDarkMode = false;
                applyTheme();
                Preferences.userNodeForPackage(getClass()).putBoolean("theme_preference", false);
                lightModeBtn.getStyleClass().add("selected");
                darkModeBtn.getStyleClass().remove("selected");
                if (settingsContent.getStyleClass().contains("dark-mode")) {
                    settingsContent.getStyleClass().remove("dark-mode");
                }
            }
        });

        darkModeBtn.setOnMouseClicked(e -> {
            if (!isDarkMode) {
                isDarkMode = true;
                applyTheme();
                Preferences.userNodeForPackage(getClass()).putBoolean("theme_preference", true);
                darkModeBtn.getStyleClass().add("selected");
                lightModeBtn.getStyleClass().remove("selected");
                if (!settingsContent.getStyleClass().contains("dark-mode")) {
                    settingsContent.getStyleClass().add("dark-mode");
                }
            }
        });

        // Language Section
        Label languageLabel = new Label(bundle.getString("settings.language"));
        languageLabel.getStyleClass().add("settings-section-title");

        // English Button
        HBox englishBtn = new HBox(12);
        englishBtn.getStyleClass().add("settings-option");
        englishBtn.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label englishIcon = new Label("🇬🇧");
        englishIcon.getStyleClass().add("settings-option-icon");
        Label englishText = new Label("English");
        englishText.getStyleClass().add("settings-option-text");
        englishBtn.getChildren().addAll(englishIcon, englishText);

        // Turkish Button
        HBox turkishBtn = new HBox(12);
        turkishBtn.getStyleClass().add("settings-option");
        turkishBtn.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label turkishIcon = new Label("🇹🇷");
        turkishIcon.getStyleClass().add("settings-option-icon");
        Label turkishText = new Label("Türkçe");
        turkishText.getStyleClass().add("settings-option-text");
        turkishBtn.getChildren().addAll(turkishIcon, turkishText);

        // Set initial language selection
        if (currentLanguage.equals("tr")) {
            turkishBtn.getStyleClass().add("selected");
        } else {
            englishBtn.getStyleClass().add("selected");
        }

        // Close button (declared early so it can be referenced in handlers)
        Button closeBtn = new Button(bundle.getString("settings.close"));
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setMaxWidth(Double.MAX_VALUE);
        closeBtn.setOnAction(e -> settingsStage.close());

        // English click handler
        englishBtn.setOnMouseClicked(e -> {
            if (!currentLanguage.equals("en")) {
                loadLanguage("en");
                englishBtn.getStyleClass().add("selected");
                turkishBtn.getStyleClass().remove("selected");

                settingsStage.setTitle(bundle.getString("settings.title"));
                titleLabel.setText(bundle.getString("settings.title"));
                themeLabel.setText(bundle.getString("settings.theme"));
                lightText.setText(bundle.getString("settings.lightMode"));
                darkText.setText(bundle.getString("settings.darkMode"));
                languageLabel.setText(bundle.getString("settings.language"));
                closeBtn.setText(bundle.getString("settings.close"));
            }
        });

        // Turkish click handler
        turkishBtn.setOnMouseClicked(e -> {
            if (!currentLanguage.equals("tr")) {
                loadLanguage("tr");
                turkishBtn.getStyleClass().add("selected");
                englishBtn.getStyleClass().remove("selected");

                settingsStage.setTitle(bundle.getString("settings.title"));
                titleLabel.setText(bundle.getString("settings.title"));
                themeLabel.setText(bundle.getString("settings.theme"));
                lightText.setText(bundle.getString("settings.lightMode"));
                darkText.setText(bundle.getString("settings.darkMode"));
                languageLabel.setText(bundle.getString("settings.language"));
                closeBtn.setText(bundle.getString("settings.close"));
            }
        });

        // Add all elements
        VBox themeOptions = new VBox(8);
        themeOptions.getChildren().addAll(lightModeBtn, darkModeBtn);

        VBox languageOptions = new VBox(8);
        languageOptions.getChildren().addAll(englishBtn, turkishBtn);

        settingsContent.getChildren().addAll(titleLabel, themeLabel, themeOptions, languageLabel, languageOptions,
                closeBtn);

        // Apply dark mode to settings popup if active
        if (isDarkMode) {
            settingsContent.getStyleClass().add("dark-mode");
        }

        Scene settingsScene = new Scene(settingsContent);
        settingsScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        settingsScene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

        settingsStage.setScene(settingsScene);
        settingsStage.showAndWait();
    }

    private void applyTheme() {
        Scene scene = viewDataImport.getScene();
        if (scene != null && scene.getRoot() != null) {
            if (isDarkMode) {
                if (!scene.getRoot().getStyleClass().contains("dark-mode")) {
                    scene.getRoot().getStyleClass().add("dark-mode");
                }
            } else {
                scene.getRoot().getStyleClass().remove("dark-mode");
            }
        }
    }

    /**
     * Apply dark mode styling to a dialog's root container and scene
     */
    private void applyDarkModeToDialog(javafx.scene.Parent root, Scene scene) {
        if (isDarkMode) {
            if (!root.getStyleClass().contains("dark-mode")) {
                root.getStyleClass().add("dark-mode");
            }
        }
        if (scene != null) {
            scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        }
    }

    /**
     * Apply dark mode styling to Alert dialogs
     */
    private void applyDarkModeToAlert(Alert alert) {
        if (isDarkMode) {
            alert.getDialogPane().getStylesheets().add(getClass().getResource("style.css").toExternalForm());
            alert.getDialogPane().getStyleClass().add("dark-mode");
        }
    }

    /**
     * Apply dark mode styling to Dialog objects
     */
    private void applyDarkModeToDialogPane(Dialog<?> dialog) {
        if (isDarkMode) {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("style.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("dark-mode");
        }
    }

    // ===== Dark Mode Color Helpers =====
    private String dmBg() {
        return isDarkMode ? "#1F2937" : "white";
    }

    private String dmBgSecondary() {
        return isDarkMode ? "#111827" : "#F9FAFB";
    }

    private String dmBgTertiary() {
        return isDarkMode ? "#374151" : "#F3F4F6";
    }

    private String dmBgCard() {
        return isDarkMode ? "#374151" : "#F9FAFB";
    }

    private String dmBorder() {
        return isDarkMode ? "#4B5563" : "#E5E7EB";
    }

    private String dmText() {
        return isDarkMode ? "#F9FAFB" : "#1F2937";
    }

    private String dmTextSecondary() {
        return isDarkMode ? "#9CA3AF" : "#6B7280";
    }

    private String dmTextTertiary() {
        return isDarkMode ? "#D1D5DB" : "#374151";
    }

    private String dmSuccess() {
        return isDarkMode ? "#34D399" : "#059669";
    }

    private String dmError() {
        return isDarkMode ? "#F87171" : "#DC2626";
    }

    private String dmSuccessBg() {
        return isDarkMode ? "#064E3B" : "#ECFDF5";
    }

    private String dmErrorBg() {
        return isDarkMode ? "#7F1D1D" : "#FEF2F2";
    }

    private String dmSuccessBorder() {
        return isDarkMode ? "#34D399" : "#10B981";
    }

    private String dmErrorBorder() {
        return isDarkMode ? "#F87171" : "#EF4444";
    }

    private String dmSuccessText() {
        return isDarkMode ? "#A7F3D0" : "#065F46";
    }

    private String dmErrorText() {
        return isDarkMode ? "#FCA5A5" : "#991B1B";
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

        // DEBUG: Check enrollments for this student
        System.out.println("=== DEBUG: Checking student " + student.getId() + " ===");
        System.out.println("Total enrollments in system: " + enrollments.size());
        long studentEnrollmentCount = enrollments.stream()
                .filter(e -> e.getStudent().getId().equals(student.getId()))
                .count();
        System.out.println("Enrollments for this student: " + studentEnrollmentCount);
        enrollments.stream()
                .filter(e -> e.getStudent().getId().equals(student.getId()))
                .forEach(e -> System.out.println("  - Enrolled in: " + e.getCourse().getCode()));

        List<Exam> exams = currentTimetable.getExamsForStudent(student);
        System.out.println("Exams found for student: " + exams.size());
        for (Exam exam : exams) {
            System.out.println("  - Exam: " + exam.getCourse().getCode() + " on " + exam.getSlot().getDate());
        }

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
            table.addCell(createCell(exam.getCourse().toString(), rowColor));
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
    private void handleValidateAll() {
        if (currentTimetable == null || currentTimetable.getExams().isEmpty()) {
            showError("No Timetable", "Please generate a timetable first.");
            return;
        }

        List<String> issues = new ArrayList<>();
        int validCount = 0;

        for (Exam exam : currentTimetable.getExams()) {
            List<Exam> others = currentTimetable.getExams().stream()
                    .filter(e -> !e.getCourse().getCode().equals(exam.getCourse().getCode()))
                    .collect(Collectors.toList());

            String error = constraintChecker.checkManualMove(exam, others, enrollments);

            if (error != null) {
                issues.add("❌ " + exam.getCourse().getCode() + ": " + error);
            } else {
                validCount++;
            }
        }

        if (issues.isEmpty()) {
            showInformation("✓ All Valid",
                    "All " + validCount + " exams pass constraint validation!\n\n" +
                            "• No classroom conflicts\n" +
                            "• No student time conflicts\n" +
                            "• All capacity requirements met\n" +
                            "• Minimum gaps satisfied");
        } else {
            List<String> reportLines = new ArrayList<>();
            reportLines.add("📊 Validation Summary:");
            reportLines.add("   ✓ Valid: " + validCount + " exams");
            reportLines.add("   ❌ Issues: " + issues.size() + " exams");
            reportLines.add("");
            reportLines.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            reportLines.addAll(issues);

            showScrollableDialog("Validation Results", reportLines);
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
        applyDarkModeToDialogPane(dialog);

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
        applyDarkModeToAlert(alert);
        alert.showAndWait();
    }

    private void showInformation(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        applyDarkModeToAlert(alert);
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
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        applyDarkModeToDialog(root, scene);

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
            empty.getStyleClass().addAll("label", "text-secondary");
            empty.setStyle("-fx-font-size: 14px;");
            emptyContent.getChildren().add(empty);
            scrollPane.setContent(emptyContent);
        } else {
            // Build Grid Calendar
            javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
            grid.setHgap(5);
            grid.setVgap(0); // No vertical gap - lines will align with rows
            grid.setPadding(new javafx.geometry.Insets(15));
            grid.setStyle("-fx-background-color: transparent;");

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

            // Find date range from FILTERED exams (not all exams) to show only relevant
            // days
            LocalDate startDate;
            LocalDate endDate;

            if (!exams.isEmpty()) {
                startDate = exams.stream()
                        .map(e -> e.getSlot().getDate())
                        .min(LocalDate::compareTo)
                        .orElse(LocalDate.now());
                endDate = exams.stream()
                        .map(e -> e.getSlot().getDate())
                        .max(LocalDate::compareTo)
                        .orElse(LocalDate.now());
            } else {
                startDate = LocalDate.now();
                endDate = LocalDate.now();
            }

            // Calculate number of days to show (minimum 7, but expand to include all exam
            // dates)
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
            int numDays = (int) Math.max(7, daysBetween);

            List<LocalDate> weekDates = new ArrayList<>();
            for (int i = 0; i < numDays; i++) {
                weekDates.add(startDate.plusDays(i));
            }

            // Add column constraints for each day - wider columns to show full course codes
            for (int i = 0; i < weekDates.size(); i++) {
                javafx.scene.layout.ColumnConstraints dayCol = new javafx.scene.layout.ColumnConstraints();
                dayCol.setMinWidth(130);
                dayCol.setPrefWidth(150);
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
                    timeLabel.getStyleClass().addAll("time-label");
                    // Override default padding/translate if needed, but color comes from CSS
                    timeLabel.setStyle("-fx-font-size: 11px;");
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
                dayHeader.getStyleClass().add("day-header-box");

                Label dayLabel = new Label(date.format(dayFormatter));
                dayLabel.getStyleClass().add("grid-header");
                Label dateLabel = new Label(date.format(dateFormatter));
                dateLabel.getStyleClass().addAll("label", "text-secondary");
                dateLabel.setStyle("-fx-font-size: 11px;");

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
                    courseLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
                    courseLabel.setWrapText(true);
                    courseLabel.setMaxWidth(Double.MAX_VALUE);

                    Label roomLabel = new Label("📍 " + exam.getClassroom().getName());
                    roomLabel.getStyleClass().add("exam-card-detail");
                    roomLabel.setStyle("-fx-font-size: 10px;");
                    roomLabel.setWrapText(true);
                    roomLabel.setMaxWidth(Double.MAX_VALUE);

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
        footer.getStyleClass().add("modal-footer");

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> dialog.close());
        footer.getChildren().add(closeBtn);

        root.getChildren().addAll(header, scrollPane, footer);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/examplanner/ui/style.css").toExternalForm());
        applyDarkModeToDialog(root, scene);

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

        // Add row factory for context menu and double-click edit
        examTableView.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Exam> row = new javafx.scene.control.TableRow<>();

            // Context Menu
            javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();

            javafx.scene.control.MenuItem editItem = new javafx.scene.control.MenuItem("Edit Exam");
            editItem.setOnAction(e -> {
                Exam exam = row.getItem();
                if (exam != null)
                    showExamDetails(exam);
            });

            javafx.scene.control.MenuItem quickDateItem = new javafx.scene.control.MenuItem("📅 Quick Change Date");
            quickDateItem.setOnAction(e -> {
                Exam exam = row.getItem();
                if (exam != null)
                    showQuickDateChange(exam);
            });

            javafx.scene.control.MenuItem quickRoomItem = new javafx.scene.control.MenuItem(
                    "🏫 Quick Change Classroom");
            quickRoomItem.setOnAction(e -> {
                Exam exam = row.getItem();
                if (exam != null)
                    showQuickClassroomChange(exam);
            });

            javafx.scene.control.MenuItem quickTimeItem = new javafx.scene.control.MenuItem("🕒 Quick Change Time");
            quickTimeItem.setOnAction(e -> {
                Exam exam = row.getItem();
                if (exam != null)
                    showQuickTimeChange(exam);
            });

            javafx.scene.control.SeparatorMenuItem separator = new javafx.scene.control.SeparatorMenuItem();

            javafx.scene.control.MenuItem validateItem = new javafx.scene.control.MenuItem("✓ Validate This Exam");
            validateItem.setOnAction(e -> {
                Exam exam = row.getItem();
                if (exam != null)
                    validateSingleExam(exam);
            });

            contextMenu.getItems().addAll(editItem, separator, quickDateItem, quickTimeItem, quickRoomItem,
                    new javafx.scene.control.SeparatorMenuItem(), validateItem);

            // Only show context menu for non-empty rows
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((javafx.scene.control.ContextMenu) null)
                            .otherwise(contextMenu));

            // Double-click to edit
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showExamDetails(row.getItem());
                }
            });

            return row;
        });

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

    // Quick Edit Methods for FR5
    private void showQuickDateChange(Exam exam) {
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Quick Date Change");
        dialog.setHeaderText("Change date for: " + exam.getCourse().getCode());
        applyDarkModeToDialogPane(dialog);

        ButtonType applyBtn = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyBtn, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new javafx.geometry.Insets(20));

        Label currentLabel = new Label(
                "Current: " + exam.getSlot().getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy (EEEE)")));
        currentLabel.getStyleClass().addAll("label", "text-secondary");

        javafx.scene.control.DatePicker datePicker = new javafx.scene.control.DatePicker(exam.getSlot().getDate());
        datePicker.setMaxWidth(Double.MAX_VALUE);

        // Validation feedback
        Label validationLabel = new Label();
        validationLabel.setWrapText(true);
        validationLabel.setStyle("-fx-font-size: 12px;");

        datePicker.valueProperty().addListener((obs, old, newDate) -> {
            if (newDate != null) {
                ExamSlot newSlot = new ExamSlot(newDate, exam.getSlot().getStartTime(), exam.getSlot().getEndTime());
                Exam tempExam = new Exam(exam.getCourse(), exam.getClassroom(), newSlot);
                List<Exam> others = currentTimetable.getExams().stream()
                        .filter(e -> !e.getCourse().getCode().equals(exam.getCourse().getCode()))
                        .collect(Collectors.toList());
                String error = constraintChecker.checkManualMove(tempExam, others, enrollments);

                if (error == null) {
                    validationLabel.setText("✓ Valid change");
                    validationLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + dmSuccess() + ";");
                } else {
                    validationLabel.setText("⚠ " + error);
                    validationLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + dmError() + ";");
                }
            }
        });

        // Trigger initial validation
        datePicker.fireEvent(new javafx.event.ActionEvent());

        content.getChildren().addAll(currentLabel, datePicker, validationLabel);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == applyBtn)
                return datePicker.getValue();
            return null;
        });

        dialog.showAndWait().ifPresent(newDate -> {
            ExamSlot newSlot = new ExamSlot(newDate, exam.getSlot().getStartTime(), exam.getSlot().getEndTime());
            exam.setSlot(newSlot);
            repository.saveTimetable(currentTimetable);
            refreshTimetable();
            showInformation("Success", "Date updated successfully!");
        });
    }

    private void showQuickClassroomChange(Exam exam) {
        Dialog<Classroom> dialog = new Dialog<>();
        dialog.setTitle("Quick Classroom Change");
        dialog.setHeaderText("Change classroom for: " + exam.getCourse().getCode());
        applyDarkModeToDialogPane(dialog);

        ButtonType applyBtn = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyBtn, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new javafx.geometry.Insets(20));

        int studentCount = (int) enrollments.stream()
                .filter(e -> e.getCourse().getCode().equals(exam.getCourse().getCode()))
                .count();

        Label currentLabel = new Label("Current: " + exam.getClassroom().getName() +
                " (Capacity: " + exam.getClassroom().getCapacity() + ")");
        currentLabel.getStyleClass().addAll("label", "text-secondary");

        Label needLabel = new Label("Required capacity: " + studentCount + " students");
        needLabel.getStyleClass().addAll("label", "text-secondary");
        needLabel.setStyle("-fx-font-weight: bold;");

        javafx.scene.control.ComboBox<Classroom> classroomCombo = new javafx.scene.control.ComboBox<>();
        classroomCombo.setMaxWidth(Double.MAX_VALUE);
        classroomCombo.getItems().addAll(classrooms.stream()
                .sorted(Comparator.comparing(Classroom::getName))
                .collect(Collectors.toList()));
        classroomCombo.setValue(exam.getClassroom());
        classroomCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Classroom item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String status = item.getCapacity() >= studentCount ? "✓" : "❌";
                    setText(status + " " + item.getName() + " (Capacity: " + item.getCapacity() + ")");
                    // Clear previous style classes for color
                    getStyleClass().removeAll("text-success", "text-error");

                    if (item.getCapacity() >= studentCount) {
                        getStyleClass().add("text-success");
                    } else {
                        getStyleClass().add("text-error");
                    }
                }
            }
        });
        classroomCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Classroom item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName() + " (Capacity: " + item.getCapacity() + ")");
                }
            }
        });

        // Validation feedback
        Label validationLabel = new Label();
        validationLabel.setWrapText(true);
        validationLabel.setStyle("-fx-font-size: 12px;");

        classroomCombo.valueProperty().addListener((obs, old, newRoom) -> {
            if (newRoom != null) {
                Exam tempExam = new Exam(exam.getCourse(), newRoom, exam.getSlot());
                List<Exam> others = currentTimetable.getExams().stream()
                        .filter(e -> !e.getCourse().getCode().equals(exam.getCourse().getCode()))
                        .collect(Collectors.toList());
                String error = constraintChecker.checkManualMove(tempExam, others, enrollments);

                if (error == null) {
                    validationLabel.setText("✓ Valid change");
                    validationLabel.getStyleClass().removeAll("text-error");
                    validationLabel.getStyleClass().add("text-success");
                    validationLabel.setStyle("-fx-font-size: 12px;");
                } else {
                    validationLabel.setText("⚠ " + error);
                    validationLabel.getStyleClass().removeAll("text-success");
                    validationLabel.getStyleClass().add("text-error");
                    validationLabel.setStyle("-fx-font-size: 12px;");
                }
            }
        });

        content.getChildren().addAll(currentLabel, needLabel, classroomCombo, validationLabel);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == applyBtn)
                return classroomCombo.getValue();
            return null;
        });

        dialog.showAndWait().ifPresent(newRoom -> {
            exam.setClassroom(newRoom);
            repository.saveTimetable(currentTimetable);
            refreshTimetable();
            showInformation("Success", "Classroom updated successfully!");
        });
    }

    private void showQuickTimeChange(Exam exam) {
        Dialog<LocalTime> dialog = new Dialog<>();
        dialog.setTitle("Quick Time Change");
        dialog.setHeaderText("Change time for: " + exam.getCourse().getCode());
        applyDarkModeToDialogPane(dialog);

        ButtonType applyBtn = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyBtn, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new javafx.geometry.Insets(20));

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        Label currentLabel = new Label("Current: " + exam.getSlot().getStartTime().format(timeFmt) +
                " - " + exam.getSlot().getEndTime().format(timeFmt));
        currentLabel.getStyleClass().addAll("label", "text-secondary");

        Label durationLabel = new Label("Duration: " + exam.getCourse().getExamDurationMinutes() + " minutes");
        durationLabel.getStyleClass().addAll("label", "text-secondary");

        HBox timeBox = new HBox(10);
        timeBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.control.ComboBox<String> timeCombo = new javafx.scene.control.ComboBox<>();
        for (int hour = 9; hour <= 17; hour++) {
            for (int min = 0; min < 60; min += 30) {
                timeCombo.getItems().add(String.format("%02d:%02d", hour, min));
            }
        }
        timeCombo.setValue(exam.getSlot().getStartTime().format(timeFmt));

        Label endLabel = new Label("→ " + exam.getSlot().getEndTime().format(timeFmt));
        endLabel.getStyleClass().addAll("label", "text-secondary");
        endLabel.setStyle("-fx-font-weight: bold;");

        timeCombo.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                LocalTime newStart = LocalTime.parse(newVal, timeFmt);
                LocalTime newEnd = newStart.plusMinutes(exam.getCourse().getExamDurationMinutes());
                endLabel.setText("→ " + newEnd.format(timeFmt));
            }
        });

        timeBox.getChildren().addAll(new Label("Start:"), timeCombo, endLabel);

        // Validation feedback
        Label validationLabel = new Label();
        validationLabel.setWrapText(true);
        validationLabel.setStyle("-fx-font-size: 12px;");

        timeCombo.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                LocalTime newStart = LocalTime.parse(newVal, timeFmt);
                LocalTime newEnd = newStart.plusMinutes(exam.getCourse().getExamDurationMinutes());
                ExamSlot newSlot = new ExamSlot(exam.getSlot().getDate(), newStart, newEnd);
                Exam tempExam = new Exam(exam.getCourse(), exam.getClassroom(), newSlot);
                List<Exam> others = currentTimetable.getExams().stream()
                        .filter(e -> !e.getCourse().getCode().equals(exam.getCourse().getCode()))
                        .collect(Collectors.toList());
                String error = constraintChecker.checkManualMove(tempExam, others, enrollments);

                if (error == null) {
                    validationLabel.setText("✓ Valid change");
                    validationLabel.getStyleClass().removeAll("text-error");
                    validationLabel.getStyleClass().add("text-success");
                    validationLabel.setStyle("-fx-font-size: 12px;");
                } else {
                    validationLabel.setText("⚠ " + error);
                    validationLabel.getStyleClass().removeAll("text-success");
                    validationLabel.getStyleClass().add("text-error");
                    validationLabel.setStyle("-fx-font-size: 12px;");
                }
            }
        });

        // Trigger initial validation
        timeCombo.fireEvent(new javafx.event.ActionEvent());

        content.getChildren().addAll(currentLabel, durationLabel, timeBox, validationLabel);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == applyBtn) {
                String val = timeCombo.getValue();
                return val != null ? LocalTime.parse(val, timeFmt) : null;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newStart -> {
            LocalTime newEnd = newStart.plusMinutes(exam.getCourse().getExamDurationMinutes());
            ExamSlot newSlot = new ExamSlot(exam.getSlot().getDate(), newStart, newEnd);
            exam.setSlot(newSlot);
            repository.saveTimetable(currentTimetable);
            refreshTimetable();
            showInformation("Success", "Time updated successfully!");
        });
    }

    private void validateSingleExam(Exam exam) {
        List<Exam> others = currentTimetable.getExams().stream()
                .filter(e -> !e.getCourse().getCode().equals(exam.getCourse().getCode()))
                .collect(Collectors.toList());

        String error = constraintChecker.checkManualMove(exam, others, enrollments);

        if (error == null) {
            showInformation("Validation Passed", "✓ This exam has no constraint violations.\n\n" +
                    "Course: " + exam.getCourse().getCode() + "\n" +
                    "Date: " + exam.getSlot().getDate() + "\n" +
                    "Time: " + exam.getSlot().getStartTime() + " - " + exam.getSlot().getEndTime() + "\n" +
                    "Room: " + exam.getClassroom().getName());
        } else {
            showWarning("Validation Failed", "⚠ Constraint violation detected:\n\n" + error + "\n\n" +
                    "Click Edit to modify this exam.");
        }
    }

    // renderTimetableGrid, addTimeGuides, and arrangeDayExams methods removed (now
    // using TableView)

    private void refreshDashboard() {
        if (currentTimetable == null || currentTimetable.getExams().isEmpty()) {
            // Clear charts when no data
            if (chartExamsPerDay != null) {
                chartExamsPerDay.getData().clear();
            }
            if (chartRoomUsage != null) {
                chartRoomUsage.getData().clear();
            }
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

        // Create a map for date lookup
        Map<String, LocalDate> dateMap = new HashMap<>();

        // Sort by date
        examsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String dateStr = entry.getKey().format(fmt);
                    dateMap.put(dateStr, entry.getKey());
                    series.getData()
                            .add(new javafx.scene.chart.XYChart.Data<>(dateStr, entry.getValue()));
                });

        chartExamsPerDay.getData().add(series);

        // 2. Room Utilization (Pie Chart)
        chartRoomUsage.getData().clear();
        Map<String, Long> roomUsage = currentTimetable.getExams().stream()
                .collect(Collectors.groupingBy(e -> e.getClassroom().getName(), Collectors.counting()));

        roomUsage.forEach((room, count) -> {
            javafx.scene.chart.PieChart.Data pieData = new javafx.scene.chart.PieChart.Data(room, count);
            chartRoomUsage.getData().add(pieData);
        });

        // Add click handlers after the scene is rendered using Platform.runLater
        javafx.application.Platform.runLater(() -> {
            // Bar chart click handlers
            for (javafx.scene.chart.XYChart.Data<String, Number> data : series.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-cursor: hand;");
                    data.getNode().setOnMouseClicked(event -> {
                        LocalDate clickedDate = dateMap.get(data.getXValue());
                        if (clickedDate != null) {
                            showExamsForDate(clickedDate);
                        }
                    });
                    data.getNode()
                            .setOnMouseEntered(e -> data.getNode().setStyle("-fx-cursor: hand; -fx-opacity: 0.7;"));
                    data.getNode()
                            .setOnMouseExited(e -> data.getNode().setStyle("-fx-cursor: hand; -fx-opacity: 1.0;"));
                }
            }

            // Pie chart click handlers
            for (javafx.scene.chart.PieChart.Data pieData : chartRoomUsage.getData()) {
                if (pieData.getNode() != null) {
                    pieData.getNode().setStyle("-fx-cursor: hand;");
                    pieData.getNode().setOnMouseClicked(event -> {
                        showExamsForClassroom(pieData.getName());
                    });
                    pieData.getNode().setOnMouseEntered(e -> {
                        pieData.getNode().setStyle("-fx-cursor: hand; -fx-opacity: 0.8;");
                    });
                    pieData.getNode().setOnMouseExited(e -> {
                        pieData.getNode().setStyle("-fx-cursor: hand; -fx-opacity: 1.0;");
                    });
                }
            }
        });
    }

    private void showExamsForDate(LocalDate date) {
        List<Exam> exams = currentTimetable.getExams().stream()
                .filter(e -> e.getSlot().getDate().equals(date))
                .sorted(Comparator.comparing(e -> e.getSlot().getStartTime()))
                .collect(Collectors.toList());

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMMM yyyy, EEEE");
        showExamListDialog("📅 " + date.format(dateFmt), exams.size() + " exam(s) scheduled", exams);
    }

    private void showExamsForClassroom(String classroomName) {
        List<Exam> exams = currentTimetable.getExams().stream()
                .filter(e -> e.getClassroom().getName().equals(classroomName))
                .sorted(Comparator.comparing((Exam e) -> e.getSlot().getDate())
                        .thenComparing(e -> e.getSlot().getStartTime()))
                .collect(Collectors.toList());

        showExamListDialog("🏫 " + classroomName, exams.size() + " exam(s) in this classroom", exams);
    }

    private void showExamListDialog(String titleText, String subtitleText, List<Exam> exams) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox();
        root.getStyleClass().add("modal-window");
        root.setPrefSize(550, 450);

        // Header
        VBox header = new VBox(5);
        header.getStyleClass().add("modal-header");

        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        title.setStyle("-fx-font-size: 18px;");

        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().addAll("label", "text-secondary");
        subtitle.setStyle("-fx-font-size: 13px;");

        header.getChildren().addAll(title, subtitle);

        // Content - Exam List
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 15; -fx-background-color: " + dmBg() + ";");
        javafx.scene.layout.VBox.setVgrow(content, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        javafx.scene.layout.VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        VBox examList = new VBox(8);
        examList.setStyle("-fx-padding: 5;");

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (Exam exam : exams) {
            HBox examCard = new HBox(15);
            examCard.setStyle("-fx-background-color: " + dmBgCard() + "; -fx-padding: 12; -fx-background-radius: 8; " +
                    "-fx-border-color: " + dmBorder() + "; -fx-border-radius: 8;");
            examCard.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // Time/Date info
            VBox timeInfo = new VBox(2);
            timeInfo.setMinWidth(80);
            Label timeLabel = new Label(exam.getSlot().getStartTime().format(timeFmt) + " - " +
                    exam.getSlot().getEndTime().format(timeFmt));
            timeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #8B5CF6; -fx-font-size: 12px;");
            Label dateLabel = new Label(exam.getSlot().getDate().format(dateFmt));
            dateLabel.setStyle("-fx-text-fill: " + dmTextSecondary() + "; -fx-font-size: 11px;");
            timeInfo.getChildren().addAll(timeLabel, dateLabel);

            // Separator
            javafx.scene.shape.Line separator = new javafx.scene.shape.Line(0, 0, 0, 30);
            separator.setStroke(javafx.scene.paint.Color.web("#E5E7EB"));

            // Course info
            VBox courseInfo = new VBox(2);
            Label codeLabel = new Label(exam.getCourse().getCode());
            codeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + dmText() + "; -fx-font-size: 13px;");
            courseInfo.getChildren().add(codeLabel);

            // Only show name if it's different from code
            if (!exam.getCourse().getName().equals(exam.getCourse().getCode())) {
                Label nameLabel = new Label(exam.getCourse().getName());
                nameLabel.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 12px;");
                nameLabel.setWrapText(true);
                courseInfo.getChildren().add(nameLabel);
            }
            HBox.setHgrow(courseInfo, javafx.scene.layout.Priority.ALWAYS);

            // Room info
            Label roomLabel = new Label("📍 " + exam.getClassroom().getName());
            roomLabel.setStyle("-fx-text-fill: " + dmTextSecondary() + "; -fx-font-size: 11px;");

            examCard.getChildren().addAll(timeInfo, separator, courseInfo, roomLabel);
            examList.getChildren().add(examCard);
        }

        if (exams.isEmpty()) {
            Label emptyLabel = new Label("No exams found.");
            emptyLabel.setStyle("-fx-text-fill: " + dmTextSecondary() + "; -fx-font-size: 14px;");
            examList.getChildren().add(emptyLabel);
            examList.setAlignment(javafx.geometry.Pos.CENTER);
        }

        scrollPane.setContent(examList);
        content.getChildren().add(scrollPane);

        // Footer
        HBox footer = new HBox();
        footer.setStyle("-fx-padding: 15; -fx-alignment: center-right; -fx-background-color: " + dmBgTertiary() + "; " +
                "-fx-background-radius: 0 0 12 12;");

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> dialog.close());
        footer.getChildren().add(closeBtn);

        root.getChildren().addAll(header, content, footer);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/examplanner/ui/style.css").toExternalForm());
        applyDarkModeToDialog(root, scene);

        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showExamDetails(Exam exam) {
        List<Student> students = enrollments.stream()
                .filter(e -> e.getCourse().getCode().equals(exam.getCourse().getCode()))
                .map(Enrollment::getStudent)
                .sorted(Comparator.comparing(Student::getName))
                .collect(Collectors.toList());

        // Create Custom Dialog Stage
        Stage dialog = new Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);

        // Root Container
        VBox root = new VBox();
        root.getStyleClass().add("modal-window");
        root.setPrefSize(550, 650);

        // Header
        VBox header = new VBox(5);
        header.getStyleClass().add("modal-header");

        Label title = new Label("✏️ Edit Exam: " + exam.getCourse().getName());
        title.getStyleClass().add("section-title");
        title.setStyle("-fx-font-size: 18px;");

        Label subtitle = new Label(exam.getCourse().getCode() + " • " + students.size() + " Students enrolled");
        subtitle.getStyleClass().addAll("label", "text-secondary");

        header.getChildren().addAll(title, subtitle);

        // Content - Edit Form
        VBox content = new VBox(15);
        content.getStyleClass().add("modal-content");
        content.setStyle("-fx-padding: 20;");
        javafx.scene.layout.VBox.setVgrow(content, javafx.scene.layout.Priority.ALWAYS);

        // Validation feedback area
        HBox validationBox = new HBox(10);
        validationBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        validationBox.setStyle("-fx-padding: 12; -fx-background-radius: 8;");
        validationBox.setVisible(false);
        validationBox.setManaged(false);

        Label validationIcon = new Label("✓");
        validationIcon.setStyle("-fx-font-size: 16px;");
        Label validationLabel = new Label("");
        validationLabel.setWrapText(true);
        validationLabel.setStyle("-fx-font-size: 13px;");
        validationBox.getChildren().addAll(validationIcon, validationLabel);

        // Date Picker
        VBox dateSection = new VBox(5);
        Label dateLabel = new Label("📅 Exam Date");
        dateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #374151;");
        javafx.scene.control.DatePicker datePicker = new javafx.scene.control.DatePicker(exam.getSlot().getDate());
        datePicker.setMaxWidth(Double.MAX_VALUE);
        datePicker.setStyle("-fx-font-size: 14px;");
        dateSection.getChildren().addAll(dateLabel, datePicker);

        // Time Selection
        VBox timeSection = new VBox(5);
        Label timeLabel = new Label("🕒 Time Slot");
        timeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #374151;");

        HBox timeBox = new HBox(10);
        timeBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.control.ComboBox<String> startTimeCombo = new javafx.scene.control.ComboBox<>();
        startTimeCombo.setStyle("-fx-font-size: 14px;");
        startTimeCombo.setPrefWidth(120);

        // Populate time slots (09:00 - 18:00)
        for (int hour = 9; hour <= 17; hour++) {
            for (int min = 0; min < 60; min += 30) {
                startTimeCombo.getItems().add(String.format("%02d:%02d", hour, min));
            }
        }
        startTimeCombo.setValue(exam.getSlot().getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));

        Label toLabel = new Label("to");
        toLabel.setStyle("-fx-text-fill: #6B7280;");

        Label endTimeLabel = new Label(exam.getSlot().getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        endTimeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #374151; -fx-font-weight: bold;");

        Label durationLabel = new Label("(" + exam.getCourse().getExamDurationMinutes() + " min)");
        durationLabel.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 12px;");

        timeBox.getChildren().addAll(startTimeCombo, toLabel, endTimeLabel, durationLabel);
        timeSection.getChildren().addAll(timeLabel, timeBox);

        // Update end time when start time changes
        startTimeCombo.setOnAction(e -> {
            String startStr = startTimeCombo.getValue();
            if (startStr != null) {
                LocalTime newStart = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"));
                LocalTime newEnd = newStart.plusMinutes(exam.getCourse().getExamDurationMinutes());
                endTimeLabel.setText(newEnd.format(DateTimeFormatter.ofPattern("HH:mm")));
            }
        });

        // Classroom Selection
        VBox classroomSection = new VBox(5);
        Label classroomLabel = new Label("🏫 Classroom");
        classroomLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #374151;");

        javafx.scene.control.ComboBox<Classroom> classroomCombo = new javafx.scene.control.ComboBox<>();
        classroomCombo.setMaxWidth(Double.MAX_VALUE);
        classroomCombo.setStyle("-fx-font-size: 14px;");
        classroomCombo.getItems().addAll(classrooms.stream()
                .sorted(Comparator.comparing(Classroom::getName))
                .collect(Collectors.toList()));
        classroomCombo.setValue(exam.getClassroom());
        classroomCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Classroom item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName() + " (Capacity: " + item.getCapacity() + ")");
                }
            }
        });
        classroomCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Classroom item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName() + " (Capacity: " + item.getCapacity() + ")");
                }
            }
        });

        // Capacity indicator
        HBox capacityBox = new HBox(8);
        capacityBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label capacityIndicator = new Label();
        updateCapacityIndicator(capacityIndicator, classroomCombo.getValue(), students.size());

        capacityBox.getChildren().add(capacityIndicator);
        classroomSection.getChildren().addAll(classroomLabel, classroomCombo, capacityBox);

        classroomCombo.setOnAction(
                e -> updateCapacityIndicator(capacityIndicator, classroomCombo.getValue(), students.size()));

        // Live Validation Function
        Runnable validateChanges = () -> {
            LocalDate newDate = datePicker.getValue();
            String startStr = startTimeCombo.getValue();
            Classroom newClassroom = classroomCombo.getValue();

            if (newDate == null || startStr == null || newClassroom == null) {
                return;
            }

            LocalTime newStart = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime newEnd = newStart.plusMinutes(exam.getCourse().getExamDurationMinutes());
            ExamSlot newSlot = new ExamSlot(newDate, newStart, newEnd);

            // Create a temporary exam for validation
            Exam tempExam = new Exam(exam.getCourse(), newClassroom, newSlot);

            // Create list without original exam for checking
            List<Exam> otherExams = currentTimetable.getExams().stream()
                    .filter(e -> !e.getCourse().getCode().equals(exam.getCourse().getCode()))
                    .collect(Collectors.toList());

            String error = constraintChecker.checkManualMove(tempExam, otherExams, enrollments);

            validationBox.setVisible(true);
            validationBox.setManaged(true);

            if (error == null) {
                validationBox.setStyle(
                        "-fx-padding: 12; -fx-background-radius: 8; -fx-background-color: " + dmSuccessBg()
                                + "; -fx-border-color: " + dmSuccessBorder() + "; -fx-border-radius: 8;");
                validationIcon.setText("✓");
                validationIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: " + dmSuccess() + ";");
                validationLabel.setText("All constraints satisfied. This change is valid.");
                validationLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + dmSuccessText() + ";");
            } else {
                validationBox.setStyle(
                        "-fx-padding: 12; -fx-background-radius: 8; -fx-background-color: " + dmErrorBg()
                                + "; -fx-border-color: " + dmErrorBorder() + "; -fx-border-radius: 8;");
                validationIcon.setText("⚠");
                validationIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: " + dmError() + ";");
                validationLabel.setText("Conflict: " + error);
                validationLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + dmErrorText() + ";");
            }
        };

        // Add change listeners for live validation
        datePicker.valueProperty().addListener((obs, old, newVal) -> validateChanges.run());
        startTimeCombo.valueProperty().addListener((obs, old, newVal) -> validateChanges.run());
        classroomCombo.valueProperty().addListener((obs, old, newVal) -> validateChanges.run());

        // Current Info Section
        VBox currentInfoSection = new VBox(8);
        currentInfoSection
                .setStyle("-fx-background-color: " + dmBgTertiary() + "; -fx-padding: 12; -fx-background-radius: 8;");
        Label currentInfoTitle = new Label("📋 Current Assignment");
        currentInfoTitle
                .setStyle("-fx-font-weight: bold; -fx-text-fill: " + dmTextTertiary() + "; -fx-font-size: 13px;");

        // Store original values for history tracking
        final String originalDate = exam.getSlot().getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        final String originalStartTime = exam.getSlot().getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        final String originalEndTime = exam.getSlot().getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        final String originalClassroom = exam.getClassroom().getName();

        Label currentInfoText = new Label(String.format("Date: %s | Time: %s - %s | Room: %s",
                originalDate, originalStartTime, originalEndTime, originalClassroom));
        currentInfoText.setStyle("-fx-text-fill: " + dmTextSecondary() + "; -fx-font-size: 12px;");
        currentInfoSection.getChildren().addAll(currentInfoTitle, currentInfoText);

        // Enrolled Students Section (Clickable Button to show popup)
        HBox studentsSection = new HBox(6);
        studentsSection.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        studentsSection.setStyle(
                "-fx-padding: 8 12; -fx-background-color: #EEF2FF; -fx-background-radius: 6; -fx-cursor: hand;");

        Label studentsIcon = new Label("👥");
        studentsIcon.setStyle("-fx-font-size: 13px;");

        Label studentsLabel = new Label("Enrolled Students (" + students.size() + ")");
        studentsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4F46E5;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Label arrowIcon = new Label("›");
        arrowIcon.setStyle("-fx-font-size: 14px; -fx-text-fill: #4F46E5; -fx-font-weight: bold;");

        studentsSection.getChildren().addAll(studentsIcon, studentsLabel, spacer, arrowIcon);

        // Hover effect
        studentsSection.setOnMouseEntered(e -> studentsSection.setStyle(
                "-fx-padding: 8 12; -fx-background-color: #E0E7FF; -fx-background-radius: 6; -fx-cursor: hand;"));
        studentsSection.setOnMouseExited(e -> studentsSection.setStyle(
                "-fx-padding: 8 12; -fx-background-color: #EEF2FF; -fx-background-radius: 6; -fx-cursor: hand;"));

        // Click to show students popup
        studentsSection.setOnMouseClicked(e -> showEnrolledStudentsPopup(exam.getCourse().getCode(), students));

        content.getChildren().addAll(validationBox, currentInfoSection, dateSection, timeSection, classroomSection,
                studentsSection);

        // Footer with Save and Cancel
        HBox footer = new HBox(15);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.setMinHeight(60);
        footer.setStyle("-fx-padding: 10 40 10 40; -fx-background-color: " + dmBgTertiary()
                + "; -fx-background-radius: 0 0 12 12;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("💾 Save Changes");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            LocalDate newDate = datePicker.getValue();
            String startStr = startTimeCombo.getValue();
            Classroom newClassroom = classroomCombo.getValue();

            if (newDate == null || startStr == null || newClassroom == null) {
                showError("Invalid Input", "Please fill all fields.");
                return;
            }

            LocalTime newStart = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime newEnd = newStart.plusMinutes(exam.getCourse().getExamDurationMinutes());
            ExamSlot newSlot = new ExamSlot(newDate, newStart, newEnd);

            // Create temp exam for final validation
            Exam tempExam = new Exam(exam.getCourse(), newClassroom, newSlot);
            List<Exam> otherExams = currentTimetable.getExams().stream()
                    .filter(ex -> !ex.getCourse().getCode().equals(exam.getCourse().getCode()))
                    .collect(Collectors.toList());

            String error = constraintChecker.checkManualMove(tempExam, otherExams, enrollments);

            if (error != null) {
                // Show confirmation dialog for conflict override
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Constraint Violation");
                confirmAlert.setHeaderText("⚠️ This change violates scheduling constraints");
                confirmAlert.setContentText(
                        "Conflict: " + error + "\n\nDo you want to save anyway? This may cause scheduling problems.");

                ButtonType saveAnywayBtn = new ButtonType("Save Anyway", ButtonBar.ButtonData.OK_DONE);
                ButtonType cancelBtnType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                confirmAlert.getButtonTypes().setAll(saveAnywayBtn, cancelBtnType);

                if (confirmAlert.showAndWait().orElse(cancelBtnType) != saveAnywayBtn) {
                    return;
                }
            }

            // Record edit history before applying changes
            String newDateStr = newDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String newStartStr = newStart.format(DateTimeFormatter.ofPattern("HH:mm"));
            String newEndStr = newEnd.format(DateTimeFormatter.ofPattern("HH:mm"));
            String newClassroomName = newClassroom.getName();

            StringBuilder changes = new StringBuilder();
            StringBuilder oldValues = new StringBuilder();
            StringBuilder newValues = new StringBuilder();

            if (!originalDate.equals(newDateStr)) {
                changes.append("Date changed; ");
                oldValues.append("Date: ").append(originalDate).append("; ");
                newValues.append("Date: ").append(newDateStr).append("; ");
            }
            if (!originalStartTime.equals(newStartStr)) {
                changes.append("Time changed; ");
                oldValues.append("Time: ").append(originalStartTime).append("-").append(originalEndTime).append("; ");
                newValues.append("Time: ").append(newStartStr).append("-").append(newEndStr).append("; ");
            }
            if (!originalClassroom.equals(newClassroomName)) {
                changes.append("Classroom changed; ");
                oldValues.append("Room: ").append(originalClassroom).append("; ");
                newValues.append("Room: ").append(newClassroomName).append("; ");
            }

            if (changes.length() > 0) {
                editHistory.add(new EditHistoryEntry(
                        exam.getCourse().getCode(),
                        exam.getCourse().getName(),
                        changes.toString().trim(),
                        oldValues.toString().trim(),
                        newValues.toString().trim()));
            }

            // Apply changes
            exam.setSlot(newSlot);
            exam.setClassroom(newClassroom);

            // Save to repository
            repository.saveTimetable(currentTimetable);

            // Refresh UI
            refreshTimetable();
            dialog.close();

            showInformation("Success", "Exam schedule updated successfully!");
        });

        footer.getChildren().addAll(cancelBtn, saveBtn);

        root.getChildren().addAll(header, content, footer);

        // Scene
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/examplanner/ui/style.css").toExternalForm());
        applyDarkModeToDialog(root, scene);

        dialog.setScene(scene);

        // Initial validation
        validateChanges.run();

        dialog.showAndWait();
    }

    @FXML
    private void showEditHistory() {
        Stage popup = new Stage();
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.initStyle(javafx.stage.StageStyle.DECORATED);
        popup.setTitle("Edit History");
        popup.setResizable(true);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: " + dmBg() + ";");
        root.setPrefWidth(700);
        root.setPrefHeight(500);

        // Header
        HBox header = new HBox(10);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setStyle("-fx-padding: 20; -fx-background-color: linear-gradient(to right, #6366F1, #8B5CF6);");

        Label titleIcon = new Label("📜");
        titleIcon.setStyle("-fx-font-size: 24px;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Edit History");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label subtitle = new Label(editHistory.size() + " changes recorded");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.9);");
        titleBox.getChildren().addAll(title, subtitle);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, javafx.scene.layout.Priority.ALWAYS);

        Button clearHistoryBtn = new Button("🗑 Clear History");
        clearHistoryBtn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 8 15; -fx-cursor: hand;");
        clearHistoryBtn.setOnAction(e -> {
            editHistory.clear();
            subtitle.setText("0 changes recorded");
            popup.close();
            showInformation("History Cleared", "Edit history has been cleared.");
        });

        header.getChildren().addAll(titleIcon, titleBox, headerSpacer, clearHistoryBtn);

        // Content - History List
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: " + dmBgSecondary() + ";");
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        VBox historyList = new VBox(10);
        historyList.setStyle("-fx-padding: 15;");

        if (editHistory.isEmpty()) {
            VBox emptyState = new VBox(10);
            emptyState.setAlignment(javafx.geometry.Pos.CENTER);
            emptyState.setStyle("-fx-padding: 50;");

            Label emptyIcon = new Label("📝");
            emptyIcon.setStyle("-fx-font-size: 48px;");

            Label emptyText = new Label("No edits yet");
            emptyText.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + dmTextSecondary() + ";");

            Label emptySubtext = new Label("When you edit an exam, it will appear here");
            emptySubtext.setStyle("-fx-font-size: 13px; -fx-text-fill: " + dmTextSecondary() + ";");

            emptyState.getChildren().addAll(emptyIcon, emptyText, emptySubtext);
            historyList.getChildren().add(emptyState);
        } else {
            // Show history in reverse order (newest first)
            for (int i = editHistory.size() - 1; i >= 0; i--) {
                EditHistoryEntry entry = editHistory.get(i);

                VBox entryBox = new VBox(6);
                entryBox.setStyle("-fx-background-color: " + dmBg() + "; -fx-padding: 15; -fx-background-radius: 10; " +
                        "-fx-border-color: " + dmBorder() + "; -fx-border-radius: 10;");

                // Header row
                HBox entryHeader = new HBox(10);
                entryHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label courseLabel = new Label(entry.getCourseCode());
                courseLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #4F46E5; -fx-font-size: 14px;");

                Label nameLabel = new Label(" - " + entry.getCourseName());
                nameLabel.setStyle("-fx-text-fill: " + dmTextTertiary() + "; -fx-font-size: 13px;");

                Region entrySpacer = new Region();
                HBox.setHgrow(entrySpacer, javafx.scene.layout.Priority.ALWAYS);

                Label timeLabel = new Label("🕒 " + entry.getTimestamp());
                timeLabel.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 11px;");

                entryHeader.getChildren().addAll(courseLabel, nameLabel, entrySpacer, timeLabel);

                // Change description
                Label changeLabel = new Label("📝 " + entry.getChangeDescription());
                changeLabel.setStyle("-fx-text-fill: " + dmTextSecondary() + "; -fx-font-size: 12px;");

                // Old -> New values
                HBox valuesBox = new HBox(15);
                valuesBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                valuesBox.setStyle("-fx-padding: 8 0 0 0;");

                VBox oldBox = new VBox(2);
                Label oldTitle = new Label("Before:");
                oldTitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF; -fx-font-weight: bold;");
                Label oldValue = new Label(entry.getOldValue());
                oldValue.setStyle("-fx-font-size: 11px; -fx-text-fill: #DC2626; -fx-background-color: #FEF2F2; " +
                        "-fx-padding: 4 8; -fx-background-radius: 4;");
                oldBox.getChildren().addAll(oldTitle, oldValue);

                Label arrow = new Label("→");
                arrow.setStyle("-fx-font-size: 16px; -fx-text-fill: #9CA3AF;");

                VBox newBox = new VBox(2);
                Label newTitle = new Label("After:");
                newTitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF; -fx-font-weight: bold;");
                Label newValue = new Label(entry.getNewValue());
                newValue.setStyle("-fx-font-size: 11px; -fx-text-fill: #059669; -fx-background-color: #ECFDF5; " +
                        "-fx-padding: 4 8; -fx-background-radius: 4;");
                newBox.getChildren().addAll(newTitle, newValue);

                valuesBox.getChildren().addAll(oldBox, arrow, newBox);

                entryBox.getChildren().addAll(entryHeader, changeLabel, valuesBox);
                historyList.getChildren().add(entryBox);
            }
        }

        scrollPane.setContent(historyList);

        // Footer
        HBox footer = new HBox();
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.setStyle("-fx-padding: 15 20; -fx-background-color: " + dmBgTertiary() + ";");

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> popup.close());

        footer.getChildren().add(closeBtn);

        root.getChildren().addAll(header, scrollPane, footer);

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/examplanner/ui/style.css").toExternalForm());
        applyDarkModeToDialog(root, scene);

        popup.setScene(scene);
        popup.showAndWait();
    }

    private void updateCapacityIndicator(Label indicator, Classroom classroom, int studentCount) {
        if (classroom == null) {
            indicator.setText("");
            return;
        }

        int capacity = classroom.getCapacity();
        double usage = (double) studentCount / capacity * 100;

        if (studentCount > capacity) {
            indicator.setText("❌ Capacity exceeded! " + studentCount + "/" + capacity + " students");
            indicator.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 12px; -fx-font-weight: bold;");
        } else if (usage > 90) {
            indicator.setText(
                    "⚠️ Near capacity: " + studentCount + "/" + capacity + " (" + String.format("%.0f", usage) + "%)");
            indicator.setStyle("-fx-text-fill: #D97706; -fx-font-size: 12px;");
        } else {
            indicator.setText(
                    "✓ " + studentCount + "/" + capacity + " students (" + String.format("%.0f", usage) + "% used)");
            indicator.setStyle("-fx-text-fill: #059669; -fx-font-size: 12px;");
        }
    }

    private File chooseFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        return fileChooser.showOpenDialog(btnDataImport.getScene().getWindow());
    }

    private void showEnrolledStudentsPopup(String courseCode, List<Student> students) {
        Stage popup = new Stage();
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.initStyle(javafx.stage.StageStyle.DECORATED);
        popup.setTitle("Enrolled Students - " + courseCode);
        popup.setResizable(false);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: " + dmBg() + ";");
        root.setPrefWidth(450);
        root.setPrefHeight(500);

        // Header
        HBox header = new HBox(10);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setStyle("-fx-padding: 20; -fx-background-color: linear-gradient(to right, #8B5CF6, #6366F1);");

        Label titleIcon = new Label("👥");
        titleIcon.setStyle("-fx-font-size: 24px;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Enrolled Students");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label subtitle = new Label(courseCode + " • " + students.size() + " students");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.9);");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(titleIcon, titleBox);

        // Search box
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        searchBox.setStyle("-fx-padding: 15 20; -fx-background-color: " + dmBgSecondary() + ";");

        Label searchIcon = new Label("🔍");
        searchIcon.setStyle("-fx-font-size: 14px;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search students...");
        searchField.setStyle("-fx-background-color: " + dmBg() + "; -fx-background-radius: 8; -fx-border-color: "
                + dmBorder() + "; " +
                "-fx-border-radius: 8; -fx-padding: 8 12; -fx-font-size: 13px; -fx-text-fill: " + dmText() + ";");
        searchField.setPrefWidth(350);
        HBox.setHgrow(searchField, javafx.scene.layout.Priority.ALWAYS);

        searchBox.getChildren().addAll(searchIcon, searchField);

        // Student list
        javafx.scene.control.ListView<Student> listView = new javafx.scene.control.ListView<>();
        listView.getItems().addAll(students);
        listView.setStyle("-fx-background-color: transparent; -fx-padding: 5 10;");
        listView.setPrefHeight(300);
        VBox.setVgrow(listView, javafx.scene.layout.Priority.ALWAYS);

        listView.setCellFactory(param -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Student item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox cellBox = new HBox(12);
                    cellBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    cellBox.setStyle(
                            "-fx-padding: 10 15; -fx-background-color: " + dmBgCard() + "; -fx-background-radius: 8;");

                    Label avatar = new Label("👤");
                    avatar.setStyle("-fx-font-size: 16px; -fx-background-color: #E0E7FF; " +
                            "-fx-background-radius: 50; -fx-padding: 8; -fx-min-width: 36; -fx-alignment: center;");

                    VBox infoBox = new VBox(2);
                    Label nameLabel = new Label(item.getName());
                    nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + dmText() + ";");
                    Label idLabel = new Label("ID: " + item.getId());
                    idLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + dmTextSecondary() + ";");
                    infoBox.getChildren().addAll(nameLabel, idLabel);

                    cellBox.getChildren().addAll(avatar, infoBox);
                    setGraphic(cellBox);
                    setText(null);
                    setStyle("-fx-background-color: transparent; -fx-padding: 3 5;");
                }
            }
        });

        // Search filter
        javafx.collections.ObservableList<Student> allStudents = javafx.collections.FXCollections
                .observableArrayList(students);
        javafx.collections.transformation.FilteredList<Student> filteredStudents = new javafx.collections.transformation.FilteredList<>(
                allStudents, p -> true);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredStudents.setPredicate(student -> {
                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newVal.toLowerCase();
                return student.getName().toLowerCase().contains(lowerCaseFilter) ||
                        student.getId().toLowerCase().contains(lowerCaseFilter);
            });
        });

        javafx.collections.transformation.SortedList<Student> sortedStudents = new javafx.collections.transformation.SortedList<>(
                filteredStudents);
        listView.setItems(sortedStudents);

        // Footer
        HBox footer = new HBox(10);
        footer.setAlignment(javafx.geometry.Pos.CENTER);
        footer.setStyle("-fx-padding: 15 20; -fx-background-color: " + dmBgTertiary() + ";");

        Label footerInfo = new Label("Total: " + students.size() + " students enrolled in this exam");
        footerInfo.setStyle("-fx-font-size: 12px; -fx-text-fill: " + dmTextSecondary() + ";");

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, javafx.scene.layout.Priority.ALWAYS);

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: #8B5CF6; -fx-text-fill: white; -fx-font-size: 13px; " +
                "-fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: #7C3AED; -fx-text-fill: white; " +
                "-fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: #8B5CF6; -fx-text-fill: white; " +
                "-fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;"));
        closeBtn.setOnAction(e -> popup.close());

        footer.getChildren().addAll(footerInfo, footerSpacer, closeBtn);

        root.getChildren().addAll(header, searchBox, listView, footer);

        Scene scene = new Scene(root, 450, 480);
        scene.getStylesheets().add(getClass().getResource("/com/examplanner/ui/style.css").toExternalForm());
        applyDarkModeToDialog(root, scene);

        // ESC to close
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                popup.close();
            }
        });

        popup.setScene(scene);
        popup.centerOnScreen();
        popup.showAndWait();
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        applyDarkModeToAlert(alert);
        alert.showAndWait();
    }
}
