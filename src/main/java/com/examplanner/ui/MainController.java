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
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
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
    private GridPane timetableGrid;

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

    @FXML
    private void showDataImport() {
        viewDataImport.setVisible(true);
        viewTimetable.setVisible(false);
        viewDashboard.setVisible(false);
        if (sidebar != null) {
            sidebar.setVisible(true);
            sidebar.setManaged(true);
        }
        setActive(btnDataImport);
        setInactive(btnTimetable);
        setInactive(btnDashboard);
    }

    @FXML
    private void showDashboard() {
        viewDataImport.setVisible(false);
        viewTimetable.setVisible(false);
        viewDashboard.setVisible(true);

        if (sidebar != null) {
            sidebar.setVisible(true);
            sidebar.setManaged(true);
        }

        setActive(btnDashboard);
        setInactive(btnDataImport);
        setInactive(btnTimetable);

        refreshDashboard();
    }

    @FXML
    private void showTimetable() {
        viewDataImport.setVisible(false);
        viewDashboard.setVisible(false);
        viewTimetable.setVisible(true);

        if (sidebar != null) {
            sidebar.setVisible(false);
            sidebar.setManaged(false);
        }
        setActive(btnTimetable);
        setInactive(btnDataImport);
        setInactive(btnDashboard);
        refreshTimetable();
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
                courses = dataImportService.loadCourses(file);
                repository.saveCourses(courses);
                lblCoursesStatus.setText(file.getName() + " • " + courses.size() + " courses loaded");
                lblCoursesStatus.getStyleClass().add("text-success");
            } catch (IllegalArgumentException e) {
                showWarning("Wrong File Type", e.getMessage());
                lblCoursesStatus.getStyleClass().add("text-warning"); // Optional: add styling for warnings
            } catch (Exception e) {
                showError("Error loading courses",
                        "Your file may be empty or formatted incorrectly.\\nError: " + e.getMessage());
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
                classrooms = dataImportService.loadClassrooms(file);
                repository.saveClassrooms(classrooms);
                lblClassroomsStatus.setText(file.getName() + " • " + classrooms.size() + " classrooms loaded");
                lblClassroomsStatus.getStyleClass().add("text-success");
            } catch (IllegalArgumentException e) {
                showWarning("Wrong File Type", e.getMessage());
                lblClassroomsStatus.getStyleClass().add("text-warning");
            } catch (Exception e) {
                showError("Error loading classrooms",
                        "Your file may be empty or formatted incorrectly.\\nError: " + e.getMessage());
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
                students = dataImportService.loadStudents(file);
                repository.saveStudents(students);
                lblStudentsStatus.setText(file.getName() + " • " + students.size() + " students loaded");
                lblStudentsStatus.getStyleClass().add("text-success");
            } catch (IllegalArgumentException e) {
                showWarning("Wrong File Type", e.getMessage());
                lblStudentsStatus.getStyleClass().add("text-warning");
            } catch (Exception e) {
                showError("Error loading students",
                        "Your file may be empty or formatted incorrectly.\\nError: " + e.getMessage());
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
                enrollments = dataImportService.loadAttendance(file, courses, students);
                repository.saveEnrollments(enrollments);
                lblAttendanceStatus.setText(file.getName() + " • " + enrollments.size() + " enrollments loaded");
                lblAttendanceStatus.getStyleClass().add("text-success");
            } catch (IllegalArgumentException e) {
                showWarning("Wrong File Type", e.getMessage());
                lblAttendanceStatus.getStyleClass().add("text-warning");
            } catch (Exception e) {
                showError("Error loading attendance",
                        "Your file may be empty or formatted incorrectly.\\nError: " + e.getMessage());
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

        setLoadingState(true);

        Task<ExamTimetable> task = new Task<>() {
            @Override
            protected ExamTimetable call() throws Exception {
                System.out.println("Starting timetable generation...");
                LocalDate startDate = LocalDate.now().plusDays(1);
                System.out.println("Start date: " + startDate);
                System.out.println("Calling scheduler service...");
                return schedulerService.generateTimetable(courses, classrooms, enrollments, startDate);
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

        List<String> choices = new ArrayList<>();
        choices.add("Student");
        choices.add("Course");

        javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>("Student", choices);
        dialog.setTitle("Filter Timetable");
        dialog.setHeaderText("Select Filter Type");
        dialog.setContentText("Choose what to filter by:");

        java.util.Optional<String> result = dialog.showAndWait();
        result.ifPresent(type -> {
            if (type.equals("Student")) {
                filterByStudent();
            } else {
                filterByCourse();
            }
        });
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

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox();
        root.getStyleClass().add("modal-window");
        root.setPrefSize(1000, 700);

        // Header
        VBox header = new VBox(5);
        header.getStyleClass().add("modal-header");

        Label title = new Label("Timetable for " + student.getName());
        title.getStyleClass().add("section-title");
        title.setStyle("-fx-font-size: 16px;");

        Label subtitle = new Label(exams.size() + " exams scheduled");
        subtitle.getStyleClass().addAll("label", "text-secondary");

        header.getChildren().addAll(title, subtitle);

        // Content
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("timetable-grid");
        renderTimetableGrid(grid, currentTimetable.getExams(), exams, false);
        scrollPane.setContent(grid);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

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
        root.setPrefSize(450, 600);

        // Header
        VBox header = new VBox(5);
        header.getStyleClass().add("modal-header");

        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        title.setStyle("-fx-font-size: 16px;");

        Label subtitle = new Label(exams.size() + " exams scheduled");
        subtitle.getStyleClass().addAll("label", "text-secondary");

        header.getChildren().addAll(title, subtitle);

        // Content (Agenda View)
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);

        VBox content = new VBox(10);
        content.setPadding(new javafx.geometry.Insets(15));
        content.setStyle("-fx-background-color: white;");

        // Sort exams by Date then Time
        exams.sort(Comparator.comparing((Exam e) -> e.getSlot().getDate())
                .thenComparing(e -> e.getSlot().getStartTime()));

        LocalDate lastDate = null;
        DateTimeFormatter dayHeaderFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        for (Exam exam : exams) {
            LocalDate date = exam.getSlot().getDate();

            // New Date Group
            if (!date.equals(lastDate)) {
                Label dateHeader = new Label(date.format(dayHeaderFmt));
                dateHeader.setStyle(
                        "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #4B5563; -fx-padding: 10 0 5 0;");
                if (lastDate != null)
                    parentSeparator(content); // Add separator between days
                content.getChildren().add(dateHeader);
                lastDate = date;
            }

            // Exam Card
            HBox card = new HBox(15);
            card.setStyle(
                    "-fx-background-color: #F9FAFB; -fx-border-color: #E5E7EB; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");
            card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // Time Column
            VBox timeBox = new VBox(2);
            timeBox.setAlignment(javafx.geometry.Pos.CENTER);
            timeBox.setMinWidth(60);

            Label lblStart = new Label(exam.getSlot().getStartTime().format(timeFmt));
            lblStart.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #374151;");
            Label lblEnd = new Label(exam.getSlot().getEndTime().format(timeFmt));
            lblEnd.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF;");

            timeBox.getChildren().addAll(lblStart, lblEnd);

            // Info Column
            VBox infoBox = new VBox(2);

            String courseText = exam.getCourse().getCode();
            if (!exam.getCourse().getName().equals(courseText)) {
                courseText += " - " + exam.getCourse().getName();
            }
            Label lblCourse = new Label(courseText);
            lblCourse.setWrapText(true);
            lblCourse.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #111827;");

            HBox detailsRow = new HBox(10);
            Label lblRoom = new Label("📍 " + exam.getClassroom().getName());
            lblRoom.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

            // Duration
            long duration = java.time.Duration.between(exam.getSlot().getStartTime(), exam.getSlot().getEndTime())
                    .toMinutes();
            Label lblDur = new Label("⏱ " + duration + " min");
            lblDur.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

            detailsRow.getChildren().addAll(lblRoom, lblDur);

            infoBox.getChildren().addAll(lblCourse, detailsRow);

            card.getChildren().addAll(timeBox, infoBox);
            content.getChildren().add(card);
        }

        if (exams.isEmpty()) {
            Label empty = new Label("No exams found for this selection.");
            empty.setStyle("-fx-text-fill: #9CA3AF; -fx-padding: 20;");
            content.getChildren().add(empty);
        }

        scrollPane.setContent(content);
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
                timetableGrid.getChildren().clear();
                timetableGrid.getColumnConstraints().clear();
                timetableGrid.getRowConstraints().clear();
                Label placeholder = new Label("No exams scheduled. Click 'Generate Timetable' to begin.");
                placeholder.getStyleClass().add("section-subtitle");
                timetableGrid.add(placeholder, 0, 0);
                return;
            }

            renderTimetableGrid(timetableGrid, currentTimetable.getExams(), currentTimetable.getExams(), true);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Rendering Error", "Failed to refresh timetable: " + e.getMessage());
        }
    }

    private void renderTimetableGrid(GridPane grid, List<Exam> allExamsForRange, List<Exam> examsToRender,
            boolean enableDragAndDrop) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();

        // Ensure a consistent gap model for absolute positioning inside the day
        // AnchorPane
        // (CSS may set -fx-vgap / -fx-hgap; we also set defaults for newly-created
        // grids)
        if (grid.getVgap() <= 0) {
            grid.setVgap(10);
        }
        if (grid.getHgap() <= 0) {
            grid.setHgap(10);
        }

        // --- 1. Setup Grid Dimensions (Fixed) ---
        double SLOT_HEIGHT = 50.0; // 50px per 30 mins (row height)
        double PX_PER_HALF_HOUR = SLOT_HEIGHT + grid.getVgap(); // includes visual gap between slots
        int START_HOUR = 9;
        int END_HOUR = 18;
        int TOTAL_SLOTS = (END_HOUR - START_HOUR) * 2 + 1; // 9:00 to 18:00 inclusive

        // Setup Rows
        javafx.scene.layout.RowConstraints headerRow = new javafx.scene.layout.RowConstraints();
        headerRow.setMinHeight(40);
        headerRow.setPrefHeight(40);
        grid.getRowConstraints().add(headerRow); // Row 0 (Headers)

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            javafx.scene.layout.RowConstraints row = new javafx.scene.layout.RowConstraints();
            row.setMinHeight(SLOT_HEIGHT);
            row.setPrefHeight(SLOT_HEIGHT);
            row.setMaxHeight(SLOT_HEIGHT);
            row.setVgrow(javafx.scene.layout.Priority.NEVER);
            grid.getRowConstraints().add(row);
        }

        // Setup Columns (Time Label Col + Day Cols)
        javafx.scene.layout.ColumnConstraints timeCol = new javafx.scene.layout.ColumnConstraints();
        timeCol.setMinWidth(60);
        timeCol.setPrefWidth(60);
        grid.getColumnConstraints().add(timeCol); // Col 0

        // Determine Start Date (Min Date from full timetable range)
        LocalDate startDate = LocalDate.now().plusDays(1);
        if (allExamsForRange != null && !allExamsForRange.isEmpty()) {
            startDate = allExamsForRange.stream()
                    .map(e -> e.getSlot().getDate())
                    .min(LocalDate::compareTo)
                    .orElse(LocalDate.now());
        }

        // Create list of 7 days starting from startDate
        List<LocalDate> dateList = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dateList.add(startDate.plusDays(i));
        }

        // Add constraints for each day column
        for (int i = 0; i < dateList.size(); i++) {
            javafx.scene.layout.ColumnConstraints dayCol = new javafx.scene.layout.ColumnConstraints();
            dayCol.setMinWidth(150);
            dayCol.setPrefWidth(200);
            dayCol.setHgrow(javafx.scene.layout.Priority.ALWAYS);
            grid.getColumnConstraints().add(dayCol);
        }

        // --- 2. Render Headers and Time Labels ---
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("EEE");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // Time Labels
        int row = 1;
        for (int hour = START_HOUR; hour <= END_HOUR; hour++) {
            for (int min = 0; min < 60; min += 30) {
                if (hour == END_HOUR && min > 0)
                    break;

                String timeStr = LocalTime.of(hour, min).format(timeFormatter);
                Label timeLabel = new Label(timeStr);
                timeLabel.getStyleClass().add("time-label");
                javafx.scene.layout.GridPane.setValignment(timeLabel, javafx.geometry.VPos.TOP);
                javafx.scene.layout.GridPane.setHalignment(timeLabel, javafx.geometry.HPos.RIGHT);
                grid.add(timeLabel, 0, row);
                row++;
            }
        }

        // Day Headers
        for (int i = 0; i < dateList.size(); i++) {
            LocalDate date = dateList.get(i);
            VBox header = new VBox();
            header.getStyleClass().add("grid-header");
            header.setAlignment(javafx.geometry.Pos.CENTER);

            Label dayLabel = new Label(date.format(dayFormatter));
            dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-color-text;");

            Label dateLabel = new Label(date.format(dateFormatter));
            dateLabel.setStyle("-fx-text-fill: -fx-color-text-secondary; -fx-font-size: 11px;");

            header.getChildren().addAll(dayLabel, dateLabel);
            grid.add(header, i + 1, 0);
        }

        // --- 3. Place Exams in AnchorPanes per Day ---
        String[] colors = { "purple", "orange", "pink", "blue", "green", "red" };
        List<Exam> safeExams = examsToRender == null ? java.util.Collections.emptyList() : examsToRender;

        for (int i = 0; i < dateList.size(); i++) {
            LocalDate day = dateList.get(i);
            int colIndex = i + 1;

            javafx.scene.layout.AnchorPane dayPane = new javafx.scene.layout.AnchorPane();
            grid.add(dayPane, colIndex, 1, 1, TOTAL_SLOTS);

            // Subtle horizontal guides to make slot boundaries clearer
            addTimeGuides(dayPane, TOTAL_SLOTS, PX_PER_HALF_HOUR);

            List<Exam> dayExams = safeExams.stream()
                    .filter(e -> e.getSlot().getDate().equals(day))
                    .sorted(Comparator.comparing(e -> e.getSlot().getStartTime()))
                    .collect(Collectors.toList());

            arrangeDayExams(dayPane, dayExams, PX_PER_HALF_HOUR, START_HOUR, colors, enableDragAndDrop);

            if (enableDragAndDrop) {
                final LocalDate targetDate = day;
                dayPane.setOnDragOver(event -> {
                    if (event.getGestureSource() != dayPane && event.getDragboard().hasString()) {
                        event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                    }
                    event.consume();
                });

                dayPane.setOnDragDropped(event -> {
                    javafx.scene.input.Dragboard db = event.getDragboard();
                    boolean success = false;
                    if (db.hasString()) {
                        String data = db.getString();
                        String[] parts = data.split("\\|");
                        if (parts.length == 3) {
                            String courseCode = parts[0];
                            Exam draggedExam = currentTimetable.getExams().stream()
                                    .filter(e -> e.getCourse().getCode().equals(courseCode))
                                    .findFirst()
                                    .orElse(null);

                            if (draggedExam != null) {
                                double dropY = event.getY();
                                int slotsFromTop = (int) (dropY / PX_PER_HALF_HOUR);
                                LocalTime newStartTime = LocalTime.of(START_HOUR, 0)
                                        .plusMinutes(slotsFromTop * 30);
                                LocalTime newEndTime = newStartTime
                                        .plusMinutes(draggedExam.getCourse().getExamDurationMinutes());

                                if (newEndTime.isAfter(LocalTime.of(18, 30))) {
                                    showError("Invalid Move", "Exam extends beyond 18:30.");
                                    success = false;
                                } else {
                                    Exam candidate = new Exam(draggedExam.getCourse(), draggedExam.getClassroom(),
                                            new com.examplanner.domain.ExamSlot(targetDate, newStartTime, newEndTime));

                                    String error = constraintChecker.checkManualMove(candidate,
                                            currentTimetable.getExams(), enrollments);

                                    if (error != null) {
                                        showError("Constraint Violation", error + "\n\nMove rejected.");
                                        success = false;
                                    } else {
                                        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                                                javafx.scene.control.Alert.AlertType.CONFIRMATION);
                                        confirm.setTitle("Confirm Move");
                                        confirm.setHeaderText("Re-schedule Exam");
                                        confirm.setContentText(
                                                "Move " + courseCode + " to " + targetDate + " at " + newStartTime
                                                        + "?");

                                        if (confirm.showAndWait().get() == javafx.scene.control.ButtonType.OK) {
                                            draggedExam.getSlot().setDate(targetDate);
                                            draggedExam.getSlot().setStartTime(newStartTime);
                                            draggedExam.getSlot().setEndTime(newEndTime);

                                            repository.saveTimetable(currentTimetable);
                                            refreshTimetable();
                                            success = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    event.setDropCompleted(success);
                    event.consume();
                });
            }
        }
    }

    private void addTimeGuides(javafx.scene.layout.AnchorPane pane, int totalSlots, double pxPerHalfHour) {
        // Draw behind cards and don't intercept mouse events (drag/drop, clicks)
        for (int i = 0; i <= totalSlots; i++) {
            double y = i * pxPerHalfHour;
            javafx.scene.shape.Line line = new javafx.scene.shape.Line();
            line.setStartX(0);
            line.setStartY(y);
            line.endXProperty().bind(pane.widthProperty());
            line.setEndY(y);
            line.setMouseTransparent(true);

            boolean isHourLine = (i % 2 == 0);
            line.setStroke(javafx.scene.paint.Color.web("#E5E7EB", isHourLine ? 0.9 : 0.55));
            line.setStrokeWidth(isHourLine ? 1.0 : 0.75);

            pane.getChildren().add(line);
        }
    }

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

    // Helper: Arrange exams in a day column to avoid visual overlap
    private void arrangeDayExams(javafx.scene.layout.AnchorPane pane, List<Exam> exams, double pxPerHalfHour,
            int startHour, String[] colors, boolean enableDragAndDrop) {
        if (exams.isEmpty())
            return;

        // Simple Greedy Coloring / Packing algorithm
        // 1. Calculate vertical position (top, height) for each exam
        // 2. Detect overlapping groups (clusters)
        // 3. For each cluster, distribute horizontally

        class RenderBlock {
            Exam exam;
            double top;
            double height;
            double startMin;
            double endMin;
            int colIndex = 0;
            int totalCols = 1;
            boolean isSummary = false;
            int summaryCount = 0;

            RenderBlock(Exam e) {
                this.exam = e;
                LocalTime start = e.getSlot().getStartTime();
                int minutesFromStart = (start.getHour() - startHour) * 60 + start.getMinute();
                this.top = (minutesFromStart / 30.0) * pxPerHalfHour;
                this.height = (e.getCourse().getExamDurationMinutes() / 30.0) * pxPerHalfHour;
                this.startMin = minutesFromStart;
                this.endMin = minutesFromStart + e.getCourse().getExamDurationMinutes();
            }

            RenderBlock(double startMin, double endMin, int count) {
                this.isSummary = true;
                this.summaryCount = count;
                this.startMin = startMin;
                this.endMin = endMin;
                this.top = (startMin / 30.0) * pxPerHalfHour;
                this.height = ((endMin - startMin) / 30.0) * pxPerHalfHour;
                this.colIndex = 0;
                this.totalCols = 1;
            }
        }

        List<RenderBlock> blocks = exams.stream().map(RenderBlock::new).collect(Collectors.toList());

        // Group into clusters of overlapping events
        // Two events overlap if (Start1 < End2) and (Start2 < End1)
        List<List<RenderBlock>> clusters = new ArrayList<>();
        if (!blocks.isEmpty()) {
            List<RenderBlock> currentCluster = new ArrayList<>();
            currentCluster.add(blocks.get(0));
            clusters.add(currentCluster);

            double clusterEnd = blocks.get(0).endMin;

            for (int i = 1; i < blocks.size(); i++) {
                RenderBlock b = blocks.get(i);
                if (b.startMin < clusterEnd) {
                    // Overlaps with the current cluster context
                    currentCluster.add(b);
                    clusterEnd = Math.max(clusterEnd, b.endMin);
                } else {
                    // New cluster
                    currentCluster = new ArrayList<>();
                    currentCluster.add(b);
                    clusters.add(currentCluster);
                    clusterEnd = b.endMin;
                }
            }
        }

        List<RenderBlock> finalBlocksToRender = new ArrayList<>();
        int MAX_COLS_THRESHOLD = 5;

        // Process each cluster to assign columns
        for (List<RenderBlock> cluster : clusters) {
            // Simple packing: "First Fit".
            List<List<RenderBlock>> columns = new ArrayList<>();

            for (RenderBlock block : cluster) {
                boolean placed = false;
                for (int c = 0; c < columns.size(); c++) {
                    List<RenderBlock> colEvents = columns.get(c);
                    RenderBlock last = colEvents.get(colEvents.size() - 1);
                    if (block.startMin >= last.endMin) {
                        colEvents.add(block);
                        block.colIndex = c;
                        placed = true;
                        break;
                    }
                }

                if (!placed) {
                    List<RenderBlock> newCol = new ArrayList<>();
                    newCol.add(block);
                    columns.add(newCol);
                    block.colIndex = columns.size() - 1;
                }
            }

            int maxCols = columns.size();

            if (maxCols > MAX_COLS_THRESHOLD) {
                // Switch to Summary Mode for this cluster
                double minStart = cluster.stream().mapToDouble(b -> b.startMin).min().orElse(0);
                double maxEnd = cluster.stream().mapToDouble(b -> b.endMin).max().orElse(0);

                RenderBlock summary = new RenderBlock(minStart, maxEnd, cluster.size());
                finalBlocksToRender.add(summary);
            } else {
                for (RenderBlock block : cluster) {
                    block.totalCols = maxCols;
                }
                finalBlocksToRender.addAll(cluster);
            }
        }

        // Render to Pane
        int colorIdx = 0;
        for (RenderBlock block : finalBlocksToRender) {
            VBox card = new VBox();

            if (block.isSummary) {
                card.getStyleClass().add("exam-card-summary");
                card.setStyle(
                        "-fx-background-color: #D1D5DB; -fx-border-color: #6B7280; -fx-padding: 4; -fx-alignment: center;");
                Label title = new Label("High Density");
                title.setStyle("-fx-font-weight: bold; -fx-font-size: 10px;");
                Label detail = new Label(block.summaryCount + " exams");
                detail.setStyle("-fx-font-size: 10px;");

                // Add tooltip for summary
                javafx.scene.control.Tooltip tp = new javafx.scene.control.Tooltip(
                        block.summaryCount + " exams overlapped here.\nFilter to view details.");
                javafx.scene.control.Tooltip.install(card, tp);

                card.getChildren().addAll(title, detail);

                card.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) {
                        showInformation("High Density Area", block.summaryCount
                                + " exams are scheduled in this slot.\n\nSince showing " + block.summaryCount
                                + " columns would be unreadable, they are grouped.\n\nPlease use the 'Filter' button (Student/Course) to see specific exams day-by-day.");
                    }
                });
            } else {
                String colorClass = "exam-card-" + colors[colorIdx % colors.length];
                colorIdx++;
                card.getStyleClass().addAll("exam-card", colorClass);

                String courseText = block.exam.getCourse().getCode();
                Label title = new Label(courseText);
                title.getStyleClass().add("exam-card-title");
                if (block.totalCols > 2)
                    title.setStyle("-fx-font-size: 9px;");

                Label detail = new Label("📍 " + block.exam.getClassroom().getName());
                detail.getStyleClass().add("exam-card-detail");
                if (block.totalCols > 2)
                    detail.setStyle("-fx-font-size: 8px;");

                card.getChildren().addAll(title, detail);

                card.setOnMouseClicked(e -> showExamDetails(block.exam));

                if (enableDragAndDrop) {
                    // DRAG SOURCE
                    card.setOnDragDetected(event -> {
                        javafx.scene.input.Dragboard db = card.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                        content.putString(block.exam.getCourse().getCode() + "|" + block.exam.getSlot().getDate() + "|"
                                + block.exam.getSlot().getStartTime());
                        db.setContent(content);
                        event.consume();
                    });
                }
            }

            pane.getChildren().add(card);
            javafx.scene.layout.AnchorPane.setTopAnchor(card, block.top);

            card.setMinHeight(block.height - 2);
            card.setPrefHeight(block.height - 2);
            card.setMaxHeight(block.height - 2);

            javafx.beans.value.ChangeListener<Number> widthListener = (obs, oldVal, newVal) -> {
                double totalWidth = pane.getWidth();
                if (totalWidth <= 0)
                    return;

                // For summary, full width
                double colWidth = block.isSummary ? totalWidth : totalWidth / block.totalCols;
                double newX = block.isSummary ? 2 : (colWidth * block.colIndex) + 2;
                double newW = colWidth - 4;

                card.setLayoutX(newX);
                card.setPrefWidth(newW);
                card.setMinWidth(newW);
                card.setMaxWidth(newW);
            };

            pane.widthProperty().addListener(widthListener);
            // Initial call
            widthListener.changed(pane.widthProperty(), null, pane.getWidth());
        }
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
