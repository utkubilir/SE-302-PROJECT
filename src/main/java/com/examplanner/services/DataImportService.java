package com.examplanner.services;

import com.examplanner.domain.Classroom;
import com.examplanner.domain.Course;
import com.examplanner.domain.Enrollment;
import com.examplanner.domain.Student;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.ResourceBundle;
import java.text.MessageFormat;

public class DataImportService {
    private ResourceBundle bundle;

    public void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    private String getString(String key, Object... args) {
        if (bundle == null)
            return key + (args.length > 0 ? " " + java.util.Arrays.toString(args) : "");
        try {
            String pattern = bundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return key;
        }
    }

    private static final char BOM = '\uFEFF';

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == BOM) {
            return s.substring(1);
        }
        return s;
    }

    private enum CsvKind {
        COURSES,
        STUDENTS,
        CLASSROOMS,
        ATTENDANCE,
        UNKNOWN
    }

    private CsvKind detectCsvKind(File file) throws IOException {
        // Heuristic detection to warn users when they pick the wrong CSV.
        // Supports both:
        // - "ALL OF THE ..." single-column/semicolon formats (project dataset)
        // - header-based comma-separated formats (sampledata)
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int inspected = 0;
            boolean sawBracketList = false;
            boolean sawSemicolonCapacity = false;
            boolean sawCourseLike = false;
            boolean sawStudentLike = false;
            boolean sawRoomLike = false;
            boolean sawHeaderStudent = false;
            boolean sawHeaderCourse = false;
            boolean sawHeaderRoom = false;
            boolean sawHeaderAttendance = false;

            while ((line = br.readLine()) != null && inspected < 50) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                if (line.startsWith("ALL OF THE"))
                    continue;

                inspected++;

                String lower = line.toLowerCase(Locale.ENGLISH);

                if (line.startsWith("[") || line.startsWith("['") || line.startsWith("[\"")) {
                    sawBracketList = true;
                    continue;
                }

                // header-based CSV
                if (line.contains(",")) {
                    String[] cols = line.split(",");
                    for (String c : cols) {
                        String h = c.trim().toLowerCase(Locale.ENGLISH);
                        if (h.equals("coursecode") || h.equals("coursename") || h.equals("durationminutes")) {
                            sawHeaderCourse = true;
                        }
                        if (h.equals("studentid") || h.equals("studentname")) {
                            sawHeaderStudent = true;
                        }
                        if (h.equals("roomid") || h.equals("roomname") || h.equals("capacity")) {
                            sawHeaderRoom = true;
                        }
                    }
                    if (sawHeaderStudent && sawHeaderCourse) {
                        sawHeaderAttendance = true;
                    }
                    continue;
                }

                // semicolon classrooms: Name;Capacity
                if (line.contains(";")) {
                    String[] parts = line.split(";");
                    if (parts.length >= 2) {
                        String cap = parts[1].trim();
                        try {
                            Integer.parseInt(cap);
                            sawSemicolonCapacity = true;
                            continue;
                        } catch (NumberFormatException ignored) {
                            // fall through
                        }
                    }
                }

                // dataset patterns
                if (lower.startsWith("coursecode")) {
                    sawCourseLike = true;
                }
                if (lower.startsWith("std_id") || lower.startsWith("student") || lower.startsWith("s")) {
                    // "s" is weak; keep but will be outweighed by other signals
                    sawStudentLike = sawStudentLike || lower.startsWith("std_id") || lower.startsWith("student");
                }
                if (lower.startsWith("classroom") || lower.startsWith("room")) {
                    sawRoomLike = true;
                }
            }

            if (sawBracketList) {
                return CsvKind.ATTENDANCE;
            }
            if (sawHeaderAttendance) {
                return CsvKind.ATTENDANCE;
            }
            if (sawHeaderCourse) {
                return CsvKind.COURSES;
            }
            if (sawHeaderRoom) {
                return CsvKind.CLASSROOMS;
            }
            if (sawHeaderStudent) {
                return CsvKind.STUDENTS;
            }
            if (sawSemicolonCapacity || sawRoomLike) {
                return CsvKind.CLASSROOMS;
            }
            if (sawStudentLike && !sawCourseLike) {
                return CsvKind.STUDENTS;
            }
            if (sawCourseLike) {
                return CsvKind.COURSES;
            }
            return CsvKind.UNKNOWN;
        }
    }

    private void ensureKind(File file, CsvKind expected) throws IOException {
        CsvKind detected = detectCsvKind(file);
        if (detected != CsvKind.UNKNOWN && detected != expected) {
            throw new IllegalArgumentException(getString("import.error.wrongCsv", expected, detected));
        }
    }

    public List<Course> loadCourses(File file) throws IOException {
        ensureKind(file, CsvKind.COURSES);
        List<Course> courses = new ArrayList<>();
        Map<String, Integer> codeToLine = new HashMap<>(); // Track duplicates
        int lineNumber = 0;
        int skippedLines = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean headerCsv = false;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                // Strip BOM from the first line if present
                line = stripBom(line.trim());
                // Skip empty lines or headers
                if (line.isEmpty() || line.startsWith("ALL OF THE"))
                    continue;

                if (!headerCsv && line.contains(",")) {
                    // sampledata format
                    headerCsv = true;
                }

                if (headerCsv) {
                    // Format: CourseCode,CourseName,DurationMinutes,...
                    String[] parts = line.split(",");
                    if (parts.length < 1)
                        continue;
                    String code = parts[0].trim();
                    if (code.equalsIgnoreCase("CourseCode"))
                        continue;

                    // Skip empty codes
                    if (code.isEmpty()) {
                        System.err.println(getString("import.warning.skipping", lineNumber, "empty course code"));
                        skippedLines++;
                        continue;
                    }

                    // Check for duplicates
                    if (codeToLine.containsKey(code)) {
                        System.err.println(getString("import.warning.duplicate", "course code", code, lineNumber));
                        skippedLines++;
                        continue;
                    }

                    String name = parts.length >= 2 ? parts[1].trim() : code;
                    int duration = 120;
                    if (parts.length >= 3) {
                        try {
                            duration = Integer.parseInt(parts[2].trim());
                            if (duration <= 0) {
                                System.err.println(getString("import.warning.invalidDuration", lineNumber));
                                duration = 120;
                            }
                        } catch (NumberFormatException ignored) {
                            duration = 120;
                        }
                    }
                    codeToLine.put(code, lineNumber);
                    courses.add(new Course(code, name.isEmpty() ? code : name, duration));
                    continue;
                }

                // Format: <CourseCode> (Single column)
                String code = line;

                // Skip empty codes
                if (code.isEmpty()) {
                    skippedLines++;
                    continue;
                }

                // Check for duplicates
                if (codeToLine.containsKey(code)) {
                    System.err.println(getString("import.warning.duplicate", "course code", code, lineNumber));
                    skippedLines++;
                    continue;
                }

                codeToLine.put(code, lineNumber);
                courses.add(new Course(code, code, 120));
            }
        }

        if (courses.isEmpty()) {
            throw new IllegalArgumentException(getString("import.error.noValidCourses"));
        }

        if (skippedLines > 0) {
            System.out.println(
                    "Import complete: " + courses.size() + " courses loaded, " + skippedLines + " lines skipped.");
        }

        return courses;
    }

    public List<Classroom> loadClassrooms(File file) throws IOException {
        ensureKind(file, CsvKind.CLASSROOMS);
        List<Classroom> classrooms = new ArrayList<>();
        Map<String, Integer> idToLine = new HashMap<>();
        int lineNumber = 0;
        int skippedLines = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean headerCsv = false;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("ALL OF THE"))
                    continue;

                if (!headerCsv && line.contains(",")) {
                    headerCsv = true;
                }

                if (headerCsv) {
                    // Format: RoomID,RoomName,Capacity
                    String[] parts = line.split(",");
                    if (parts.length < 3)
                        continue;
                    String roomId = parts[0].trim();
                    if (roomId.equalsIgnoreCase("RoomID"))
                        continue;

                    if (roomId.isEmpty()) {
                        System.err.println(getString("import.warning.skipping", lineNumber, "empty room ID"));
                        skippedLines++;
                        continue;
                    }

                    if (idToLine.containsKey(roomId)) {
                        System.err.println(getString("import.warning.duplicate", "room ID", roomId, lineNumber));
                        skippedLines++;
                        continue;
                    }

                    String roomName = parts[1].trim();
                    try {
                        int capacity = Integer.parseInt(parts[2].trim());
                        if (capacity <= 0) {
                            System.err.println("Warning: Invalid capacity at line " + lineNumber + ", skipping");
                            skippedLines++;
                            continue;
                        }
                        idToLine.put(roomId, lineNumber);
                        classrooms.add(new Classroom(roomId, roomName.isEmpty() ? roomId : roomName, capacity));
                    } catch (NumberFormatException e) {
                        System.err.println(getString("import.warning.invalidCapacity", lineNumber));
                        skippedLines++;
                    }

                    continue;
                }

                // Format: Name;Capacity
                String[] parts = line.split(";");
                if (parts.length >= 2) {
                    String name = parts[0].trim();

                    if (name.isEmpty()) {
                        skippedLines++;
                        continue;
                    }

                    if (idToLine.containsKey(name)) {
                        System.err.println(getString("import.warning.duplicate", "room", name, lineNumber));
                        skippedLines++;
                        continue;
                    }

                    try {
                        int capacity = Integer.parseInt(parts[1].trim());
                        if (capacity <= 0) {
                            System.err.println("Warning: Invalid capacity at line " + lineNumber + ", skipping");
                            skippedLines++;
                            continue;
                        }
                        idToLine.put(name, lineNumber);
                        classrooms.add(new Classroom(name, name, capacity));
                    } catch (NumberFormatException e) {
                        System.err.println(getString("import.warning.invalidCapacity", lineNumber));
                        skippedLines++;
                    }
                }
            }
        }

        if (classrooms.isEmpty())

        {
            throw new IllegalArgumentException(getString("import.error.noValidClassrooms"));
        }

        if (skippedLines > 0) {
            System.out.println("Import complete: " + classrooms.size() + " classrooms loaded, " + skippedLines
                    + " lines skipped.");
        }

        return classrooms;
    }

    public List<Student> loadStudents(File file) throws IOException {
        ensureKind(file, CsvKind.STUDENTS);
        List<Student> students = new ArrayList<>();
        Map<String, Integer> idToLine = new HashMap<>();
        int lineNumber = 0;
        int skippedLines = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean headerCsv = false;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("ALL OF THE"))
                    continue;

                if (!headerCsv && line.contains(",")) {
                    headerCsv = true;
                }

                if (headerCsv) {
                    // Format: StudentID,StudentName
                    String[] parts = line.split(",");
                    if (parts.length < 1)
                        continue;
                    String id = parts[0].trim();
                    if (id.equalsIgnoreCase("StudentID"))
                        continue;

                    if (id.isEmpty()) {
                        System.err.println(getString("import.warning.skipping", lineNumber, "empty student ID"));
                        skippedLines++;
                        continue;
                    }

                    if (idToLine.containsKey(id)) {
                        System.err.println(getString("import.warning.duplicate", "student ID", id, lineNumber));
                        skippedLines++;
                        continue;
                    }

                    String name = parts.length >= 2 ? parts[1].trim() : id;
                    idToLine.put(id, lineNumber);
                    students.add(new Student(id, name.isEmpty() ? id : name));
                    continue;
                }

                // Format: <StudentID> (Single column)
                String id = line;

                if (id.isEmpty()) {
                    skippedLines++;
                    continue;
                }

                if (idToLine.containsKey(id)) {
                    System.err.println(getString("import.warning.duplicate", "student ID", id, lineNumber));
                    skippedLines++;
                    continue;
                }

                idToLine.put(id, lineNumber);
                students.add(new Student(id, id));
            }
        }

        if (students.isEmpty()) {
            throw new IllegalArgumentException(getString("import.error.noValidStudents"));
        }

        if (skippedLines > 0) {
            System.out.println(
                    "Import complete: " + students.size() + " students loaded, " + skippedLines + " lines skipped.");
        }

        return students;
    }

    public List<Enrollment> loadAttendance(File file, List<Course> courses, List<Student> existingStudents)
            throws IOException {
        ensureKind(file, CsvKind.ATTENDANCE);
        List<Enrollment> enrollments = new ArrayList<>();

        // Quick lookup maps
        Map<String, Course> courseMap = new HashMap<>();
        for (Course c : courses) {
            courseMap.put(c.getCode(), c);
        }

        Map<String, Student> studentMap = new HashMap<>();
        if (existingStudents != null) {
            for (Student s : existingStudents) {
                studentMap.put(s.getId(), s);
            }
        }

        // Detect which attendance format we have:
        // - dataset format: CourseCode line followed by [ 'Std_ID', ... ] line
        // - sampledata format: StudentID,StudentName,CourseCode per row
        boolean hasBracketLists = false;
        try (BufferedReader probe = new BufferedReader(new FileReader(file))) {
            String l;
            int checked = 0;
            while ((l = probe.readLine()) != null && checked < 50) {
                l = l.trim();
                if (l.isEmpty())
                    continue;
                if (l.startsWith("ALL OF THE"))
                    continue;
                checked++;
                if (l.startsWith("[") || l.startsWith("['") || l.startsWith("[\"")) {
                    hasBracketLists = true;
                    break;
                }
            }
        }

        if (hasBracketLists) {
            // Dataset format
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                String currentCourseCode = null;

                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty())
                        continue;
                    if (line.startsWith("ALL OF THE"))
                        continue;

                    if (!line.startsWith("[")) {
                        currentCourseCode = line;
                        // Strip BOM from the first line's course code
                        currentCourseCode = stripBom(line);
                    } else {
                        if (currentCourseCode == null)
                            continue;

                        Course course = courseMap.get(currentCourseCode);
                        if (course == null) {
                            System.err.println("Course code not found during import: " + currentCourseCode);
                            continue;
                        }

                        String content = line.substring(1, line.length() - 1); // remove [ and ]
                        if (content.isEmpty())
                            continue;

                        String[] studentIds = content.split(",");
                        for (String rawId : studentIds) {
                            String sId = rawId.trim();

                            // Remove surrounding quotes (single or double)
                            if (sId.startsWith("'") && sId.endsWith("'") && sId.length() > 2) {
                                sId = sId.substring(1, sId.length() - 1);
                            } else if (sId.startsWith("\"") && sId.endsWith("\"") && sId.length() > 2) {
                                sId = sId.substring(1, sId.length() - 1);
                            }

                            // Remove any remaining brackets that might have been included
                            sId = sId.replace("[", "").replace("]", "").trim();

                            // Skip empty IDs
                            if (sId.isEmpty()) {
                                continue;
                            }

                            Student student = studentMap.get(sId);
                            if (student == null) {
                                student = new Student(sId, sId);
                                studentMap.put(sId, student);
                            }

                            enrollments.add(new Enrollment(student, course));
                        }
                    }
                }
            }
        } else {
            // Row-based CSV format
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty())
                        continue;
                    if (line.startsWith("ALL OF THE"))
                        continue;
                    // StudentID,StudentName,CourseCode
                    String[] parts = line.split(",");
                    if (parts.length < 3)
                        continue;
                    String studentId = parts[0].trim();
                    if (studentId.equalsIgnoreCase("StudentID"))
                        continue;
                    String studentName = parts[1].trim();
                    String courseCode = parts[2].trim();

                    Course course = courseMap.get(courseCode);
                    if (course == null) {
                        System.err.println("Course code not found during import: " + courseCode);
                        continue;
                    }

                    Student student = studentMap.get(studentId);
                    if (student == null) {
                        String name = studentName.isEmpty() ? studentId : studentName;
                        student = new Student(studentId, name);
                        studentMap.put(studentId, student);
                    }

                    enrollments.add(new Enrollment(student, course));
                }
            }
        }
        return enrollments;
    }
}
