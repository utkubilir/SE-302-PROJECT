package com.example.studentdb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
}
