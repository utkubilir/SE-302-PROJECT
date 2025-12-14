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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataImportService {

    public List<Course> loadCourses(File file) throws IOException {
        List<Course> courses = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] parts = parseRow(line);

                if (parts.length >= 3) {
                    String code = parts[0];
                    String name = parts[1];
                    try {
                        int duration = Integer.parseInt(parts[2]);
                        courses.add(new Course(code, name, duration));
                    } catch (NumberFormatException e) {
                        // Skip invalid lines (like headers)
                    }
                }
            }
        }
        return courses;
    }

    public List<Classroom> loadClassrooms(File file) throws IOException {
        List<Classroom> classrooms = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] parts = parseRow(line);
                if (parts.length >= 3) {
                    String id = parts[0];
                    String name = parts[1];
                    try {
                        int capacity = Integer.parseInt(parts[2]);
                        classrooms.add(new Classroom(id, name, capacity));
                    } catch (NumberFormatException e) {
                        System.err.println("Skipping invalid classroom line: " + line);
                    }
                }
            }
        }
        return classrooms;
    }

    public List<Student> loadStudents(File file) throws IOException {
        List<Student> students = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] parts = parseRow(line);
                if (parts.length >= 2) {
                    String id = parts[0];
                    String name = parts[1];
                    // Basic validation to avoid reading headers as data
                    if (id.equalsIgnoreCase("studentId") || id.equalsIgnoreCase("id"))
                        continue;

                    students.add(new Student(id, name));
                }
            }
        }
        return students;
    }

    public List<Enrollment> loadAttendance(File file, List<Course> courses, List<Student> existingStudents)
            throws IOException {
        List<Enrollment> enrollments = new ArrayList<>();
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

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] parts = parseRow(line);
                // Support both 2-column (ID, Course) and 3-column (ID, Name, Course) formats
                if (parts.length >= 2) {
                    String studentId = parts[0];
                    String courseCode;
                    String studentName = ""; // Optional if student exists

                    if (parts.length >= 3) {
                        // Format: ID, Name, Course
                        studentName = parts[1];
                        courseCode = parts[2];
                    } else {
                        // Format: ID, Course
                        courseCode = parts[1];
                    }

                    if (studentId.equalsIgnoreCase("studentId") || courseCode.equalsIgnoreCase("courseCode"))
                        continue;

                    Student student = studentMap.get(studentId);

                    // If student not found but name is provided in CSV, create them
                    if (student == null && !studentName.isEmpty()) {
                        student = new Student(studentId, studentName);
                        studentMap.put(studentId, student);
                    } else if (student == null) {
                        // If we only have ID and student doesn't exist, we can't create a valid student
                        // object without a name (or use ID as name)
                        // For now, let's warn and skip to match strictness, OR fallback to using ID as
                        // name?
                        // Let's use ID as name as fallback to be helpful
                        student = new Student(studentId, "Unknown (" + studentId + ")");
                        studentMap.put(studentId, student);
                    }

                    Course course = courseMap.get(courseCode);

                    if (course != null) {
                        enrollments.add(new Enrollment(student, course));
                    } else {
                        System.err.println("Course not found for code: " + courseCode);
                    }
                }
            }
        }
        return enrollments;
    }

    private String[] parseRow(String line) {
        // Simple heuristic: if generic CSV, check split.
        // Prefer semicolon if present and not seemingly just part of text.
        // Actually, just split by whichever delimiter appears to separate fields.

        String[] commaParts = line.split(",");
        String[] semiParts = line.split(";");

        String[] parts;
        if (semiParts.length > commaParts.length) {
            parts = semiParts;
        } else {
            parts = commaParts;
        }

        for (int i = 0; i < parts.length; i++) {
            parts[i] = clean(parts[i]);
        }
        return parts;
    }

    private String clean(String input) {
        if (input == null)
            return "";
        // Strip BOM if present (Zero Width No-Break Space)
        String trimmed = input.replace("\uFEFF", "").trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
