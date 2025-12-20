package com.examplanner.persistence;

import com.examplanner.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DataRepository using a temporary SQLite database.
 */
class DataRepositoryTest {

    private DataRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Setup temporary DB
        File dbFile = tempDir.resolve("test_examplanner.db").toFile();
        String testDbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        DatabaseManager.setJdbcUrl(testDbUrl);
        DatabaseManager.initializeDatabase();

        repository = new DataRepository();
    }

    @Nested
    @DisplayName("Basic Entity Persistence")
    class BasicEntityTests {

        @Test
        @DisplayName("Should save and load courses")
        void shouldSaveAndLoadCourses() {
            List<Course> courses = List.of(
                    new Course("CS101", "Intro", 120),
                    new Course("CS102", "Advanced", 90));

            repository.saveCourses(courses);

            List<Course> loaded = repository.loadCourses();
            assertEquals(2, loaded.size());
            assertTrue(loaded.stream().anyMatch(c -> c.getCode().equals("CS101")));
            assertTrue(loaded.stream().anyMatch(c -> c.getCode().equals("CS102")));
        }

        @Test
        @DisplayName("Should save and load classrooms")
        void shouldSaveAndLoadClassrooms() {
            List<Classroom> classrooms = List.of(
                    new Classroom("R1", "Room 1", 50),
                    new Classroom("R2", "Room 2", 100));

            repository.saveClassrooms(classrooms);

            List<Classroom> loaded = repository.loadClassrooms();
            assertEquals(2, loaded.size());
            assertTrue(loaded.stream().anyMatch(c -> c.getId().equals("R1")));
        }

        @Test
        @DisplayName("Should save and load students")
        void shouldSaveAndLoadStudents() {
            List<Student> students = List.of(
                    new Student("S1", "Ali"),
                    new Student("S2", "Veli"));

            repository.saveStudents(students);

            List<Student> loaded = repository.loadStudents();
            assertEquals(2, loaded.size());
            assertTrue(loaded.stream().anyMatch(s -> s.getId().equals("S1")));
        }
    }

    @Nested
    @DisplayName("Relational Persistence")
    class RelationalTests {

        @Test
        @DisplayName("Should save and load enrollments")
        void shouldSaveAndLoadEnrollments() {
            // Prerequisites
            Course c1 = new Course("C1", "Course 1", 60);
            Student s1 = new Student("S1", "Student 1");

            repository.saveCourses(List.of(c1));
            repository.saveStudents(List.of(s1));

            List<Enrollment> enrollments = List.of(new Enrollment(s1, c1));

            repository.saveEnrollments(enrollments);

            // Load back requires lists of existing objects to map against
            List<Enrollment> loaded = repository.loadEnrollments(List.of(s1), List.of(c1));

            assertEquals(1, loaded.size());
            assertEquals("S1", loaded.get(0).getStudent().getId());
            assertEquals("C1", loaded.get(0).getCourse().getCode());
        }

        @Test
        @DisplayName("Should save and load timetable")
        void shouldSaveAndLoadTimetable() {
            // Prerequisites
            Course c1 = new Course("C1", "Course 1", 60);
            Classroom r1 = new Classroom("R1", "Room 1", 50);
            Student s1 = new Student("S1", "Student 1");

            repository.saveCourses(List.of(c1));
            repository.saveClassrooms(List.of(r1));
            repository.saveStudents(List.of(s1));

            Enrollment enroll = new Enrollment(s1, c1);
            repository.saveEnrollments(List.of(enroll));

            // Create Exam
            ExamSlot slot = new ExamSlot(LocalDate.of(2024, 1, 1), LocalTime.of(9, 0), LocalTime.of(10, 0));
            Exam exam = new Exam(c1, r1, slot);

            ExamTimetable timetable = new ExamTimetable(List.of(exam), List.of(enroll));

            repository.saveTimetable(timetable);

            // Load
            ExamTimetable loaded = repository.loadTimetable(List.of(c1), List.of(r1), List.of(enroll));

            assertNotNull(loaded);
            assertEquals(1, loaded.getExams().size());
            Exam loadedExam = loaded.getExams().get(0);
            assertEquals("C1", loadedExam.getCourse().getCode());
            assertEquals("R1", loadedExam.getClassroom().getId());
            assertEquals(LocalDate.of(2024, 1, 1), loadedExam.getSlot().getDate());
        }
    }

    @Test
    @DisplayName("Should clear all data")
    void shouldClearAllData() {
        repository.saveCourses(List.of(new Course("C1", "Name", 60)));
        assertEquals(1, repository.loadCourses().size());

        repository.clearAllData();

        assertEquals(0, repository.loadCourses().size());
    }
}
