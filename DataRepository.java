package com.examplanner.persistence;

import com.examplanner.domain.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataRepository {

    // --- COURSES ---
    public void saveCourses(List<Course> courses) {
        String sql = "INSERT OR REPLACE INTO courses(code, name, duration) VALUES(?,?,?)";
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (Course c : courses) {
                pstmt.setString(1, c.getCode());
                pstmt.setString(2, c.getName());
                pstmt.setInt(3, c.getExamDurationMinutes());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Course> loadCourses() {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT * FROM courses";
        try (Connection conn = DatabaseManager.connect();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Course(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("duration")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- CLASSROOMS ---
    public void saveClassrooms(List<Classroom> classrooms) {
        String sql = "INSERT OR REPLACE INTO classrooms(id, name, capacity) VALUES(?,?,?)";
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (Classroom c : classrooms) {
                pstmt.setString(1, c.getId());
                pstmt.setString(2, c.getName());
                pstmt.setInt(3, c.getCapacity());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Classroom> loadClassrooms() {
        List<Classroom> list = new ArrayList<>();
        String sql = "SELECT * FROM classrooms";
        try (Connection conn = DatabaseManager.connect();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Classroom(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getInt("capacity")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- STUDENTS ---
    public void saveStudents(List<Student> students) {
        String sql = "INSERT OR REPLACE INTO students(id, name) VALUES(?,?)";
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (Student s : students) {
                pstmt.setString(1, s.getId());
                pstmt.setString(2, s.getName());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Student> loadStudents() {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT * FROM students";
        try (Connection conn = DatabaseManager.connect();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Student(
                        rs.getString("id"),
                        rs.getString("name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- ENROLLMENTS ---
    public void saveEnrollments(List<Enrollment> enrollments) {
        String sql = "INSERT OR REPLACE INTO enrollments(student_id, course_code) VALUES(?,?)";
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (Enrollment e : enrollments) {
                pstmt.setString(1, e.getStudent().getId());
                pstmt.setString(2, e.getCourse().getCode());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Enrollment> loadEnrollments(List<Student> students, List<Course> courses) {
        List<Enrollment> list = new ArrayList<>();
        Map<String, Student> studentMap = students.stream().collect(Collectors.toMap(Student::getId, s -> s));
        Map<String, Course> courseMap = courses.stream().collect(Collectors.toMap(Course::getCode, c -> c));

        String sql = "SELECT * FROM enrollments";
        try (Connection conn = DatabaseManager.connect();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String sId = rs.getString("student_id");
                String cCode = rs.getString("course_code");
                if (studentMap.containsKey(sId) && courseMap.containsKey(cCode)) {
                    list.add(new Enrollment(studentMap.get(sId), courseMap.get(cCode)));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- TIMETABLE ---
    public void saveTimetable(ExamTimetable timetable) {
        String sqlDelete = "DELETE FROM exams";
        String sqlInsert = "INSERT INTO exams(course_code, classroom_id, date, start_time, end_time) VALUES(?,?,?,?,?)";

        try (Connection conn = DatabaseManager.connect()) {
            conn.setAutoCommit(false);

            // Clear old exams
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sqlDelete);
            }

            // Insert new exams
            try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                for (Exam exam : timetable.getExams()) {
                    pstmt.setString(1, exam.getCourse().getCode());
                    pstmt.setString(2, exam.getClassroom().getId());
                    pstmt.setString(3, exam.getSlot().getDate().toString());
                    pstmt.setString(4, exam.getSlot().getStartTime().toString());
                    pstmt.setString(5, exam.getSlot().getEndTime().toString());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ExamTimetable loadTimetable(List<Course> courses, List<Classroom> classrooms, List<Enrollment> enrollments) {
        List<Exam> exams = new ArrayList<>();
        Map<String, Course> courseMap = courses.stream().collect(Collectors.toMap(Course::getCode, c -> c));
        Map<String, Classroom> roomMap = classrooms.stream().collect(Collectors.toMap(Classroom::getId, c -> c));

        String sql = "SELECT * FROM exams";
        try (Connection conn = DatabaseManager.connect();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String cCode = rs.getString("course_code");
                String rId = rs.getString("classroom_id");
                LocalDate date = LocalDate.parse(rs.getString("date"));
                LocalTime start = LocalTime.parse(rs.getString("start_time"));
                LocalTime end = LocalTime.parse(rs.getString("end_time"));

                if (courseMap.containsKey(cCode) && roomMap.containsKey(rId)) {
                    exams.add(new Exam(
                            courseMap.get(cCode),
                            roomMap.get(rId),
                            new ExamSlot(date, start, end)));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (exams.isEmpty()) {
            return null;
        }

        return new ExamTimetable(exams, enrollments);
    }
}
