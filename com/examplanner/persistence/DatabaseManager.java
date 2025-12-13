package com.examplanner.persistence;

import com.examplanner.domain.Student;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:students.db";

    public static void initializeDatabase() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS students ("
                + "student_id TEXT PRIMARY KEY"
                + ");";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Database initialized and table created.");
        } catch (SQLException e) {
            System.out.println("Error initializing database: " + e.getMessage());
        }
    }

    public static void insertStudent(Student student) {
        String insertSQL = "INSERT OR REPLACE INTO students(student_id) VALUES(?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, student.getStudentId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error inserting student: " + e.getMessage());
        }
    }

    public static List<Student> getAllStudents() {
        List<Student> students = new ArrayList<>();
        String querySQL = "SELECT * FROM students";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                Student student = new Student(
                        rs.getString("student_id")
                );
                students.add(student);
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving students: " + e.getMessage());
        }
        return students;
    }

    public static void deleteAllStudents() {
        String deleteSQL = "DELETE FROM students";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(deleteSQL);
            System.out.println("All students deleted.");
        } catch (SQLException e) {
            System.out.println("Error deleting students: " + e.getMessage());
        }
    }
}
