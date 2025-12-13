package com.examplanner.services;

import com.examplanner.domain.Student;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvImporter {

    public static List<Student> loadStudentsFromCsv(File csvFile) throws IOException, IllegalArgumentException {
        List<Student> students = new ArrayList<>();
        String line;

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            // Read header line
            String header = br.readLine();
            if (header == null || !header.trim().equals("ALL OF THE STUDENTS IN THE SYSTEM")) {
                throw new IllegalArgumentException("Invalid CSV format. Header must be 'ALL OF THE STUDENTS IN THE SYSTEM'");
            }

            while ((line = br.readLine()) != null) {
                // The sample file has only one column: Std_ID_XXX
                String id = line.trim();

                if (!id.isEmpty()) {
                    if (!id.startsWith("Std_ID_")) {
                        throw new IllegalArgumentException("Invalid data format. Student ID must start with 'Std_ID_'. Found: " + id);
                    }
                    Student student = new Student(id);
                    students.add(student);
                }
            }
        }

        return students;
    }

    /**
     * Reads course codes from the CSV file.
     * Skips the header row and returns a list of course codes.
     */
    public static List<String> loadCoursesFromCsv(File csvFile) throws IOException {
        List<String> courseList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                // Skip empty lines safely
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Skip the first header line
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                courseList.add(line.trim());
            }
        }
        return courseList;
    }

    /**
     * Parses the attendance CSV to map Course Codes to Student IDs.
     * Handles empty lines, single quotes, and the custom 2-line format.
     */
    public static Map<String, List<String>> loadAttendanceFromCsv(File csvFile) throws IOException {
        Map<String, List<String>> attendanceMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;

            while ((line = br.readLine()) != null) {
                // Skip empty separator lines to maintain sync
                if (line.trim().isEmpty()) {
                    continue;
                }

                // First non-empty line is the Course Code
                String courseCode = line.trim();

                // Next line is the Student List
                String studentsLine = br.readLine();

                if (studentsLine != null && !studentsLine.trim().isEmpty()) {
                    List<String> studentIds = new ArrayList<>();

                    // Clean brackets [] and single/double quotes
                    String cleanLine = studentsLine.replace("[", "")
                            .replace("]", "")
                            .replace("'", "")
                            .replace("\"", "");

                    String[] ids = cleanLine.split(",");

                    for (String id : ids) {
                        if (!id.trim().isEmpty()) {
                            studentIds.add(id.trim());
                        }
                    }
                    attendanceMap.put(courseCode, studentIds);
                }
            }
        }
        return attendanceMap;
    }

}
