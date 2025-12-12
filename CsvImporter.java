package com.example.studentdb;

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


    public static List<String> loadCoursesFromCsv(File csvFile) throws IOException {
        List<String> courseList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                // Skip the header row
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                if (!line.trim().isEmpty()) {
                    courseList.add(line.trim());
                }
            }
        }
        return courseList;
    }


    public static Map<String, List<String>> loadAttendanceFromCsv(File csvFile) throws IOException {
        Map<String, List<String>> attendanceMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String courseLine;

            // Iterate through the file handling the alternating line format
            while ((courseLine = br.readLine()) != null) {

                // Line 1: Course Code
                String courseCode = courseLine.trim();

                // Line 2: List of Students (as a string)
                String studentsLine = br.readLine();

                if (studentsLine != null) {
                    List<String> studentIds = new ArrayList<>();

                    // Clean the format: ["2022...", "2023..."] -> 2022..., 2023...
                    String cleanLine = studentsLine.replace("[", "")
                            .replace("]", "")
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
