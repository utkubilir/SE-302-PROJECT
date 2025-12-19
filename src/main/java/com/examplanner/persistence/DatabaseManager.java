package com.examplanner.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String APP_DIR = System.getProperty("user.home") + java.io.File.separator + ".examplanner";
    private static final String DB_URL = "jdbc:sqlite:" + APP_DIR + java.io.File.separator + "examplanner.db";

    public static void initializeDatabase() {
        // Ensure application directory exists
        java.io.File dir = new java.io.File(APP_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn != null) {
                createTables(conn);
                System.out.println("Database initialized successfully.");
            }
        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Courses
            stmt.execute("CREATE TABLE IF NOT EXISTS courses (" +
                    "code TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "duration INTEGER NOT NULL)");

            // Classrooms
            stmt.execute("CREATE TABLE IF NOT EXISTS classrooms (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "capacity INTEGER NOT NULL)");

            // Students
            stmt.execute("CREATE TABLE IF NOT EXISTS students (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL)");

            // Enrollments
            stmt.execute("CREATE TABLE IF NOT EXISTS enrollments (" +
                    "student_id TEXT, " +
                    "course_code TEXT, " +
                    "PRIMARY KEY (student_id, course_code), " +
                    "FOREIGN KEY (student_id) REFERENCES students(id), " +
                    "FOREIGN KEY (course_code) REFERENCES courses(code))");

            // Timetable (Exams)
            stmt.execute("CREATE TABLE IF NOT EXISTS exams (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "course_code TEXT NOT NULL, " +
                    "classroom_id TEXT NOT NULL, " +
                    "date TEXT NOT NULL, " +
                    "start_time TEXT NOT NULL, " +
                    "end_time TEXT NOT NULL, " +
                    "FOREIGN KEY (course_code) REFERENCES courses(code), " +
                    "FOREIGN KEY (classroom_id) REFERENCES classrooms(id))");
        }
    }
}
