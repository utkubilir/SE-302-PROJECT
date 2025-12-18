package com.examplanner.services;

import com.examplanner.domain.Classroom;
import com.examplanner.domain.Course;
import com.examplanner.domain.Enrollment;
import com.examplanner.domain.Student;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataImportService.
 */
class DataImportServiceTest {

    private DataImportService dataImportService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        dataImportService = new DataImportService();
    }

    @Nested
    @DisplayName("Load Courses Tests")
    class LoadCoursesTests {

        @Test
        @DisplayName("Should load courses from CSV with header")
        void shouldLoadCoursesFromCsvWithHeader() throws IOException {
            File coursesFile = createTempFile("courses.csv",
                    "CourseCode,CourseName,DurationMinutes\n" +
                            "CS101,Introduction to Programming,120\n" +
                            "CS102,Data Structures,90\n" +
                            "CS103,Algorithms,60\n");

            List<Course> courses = dataImportService.loadCourses(coursesFile);

            assertEquals(3, courses.size());
            assertEquals("CS101", courses.get(0).getCode());
            assertEquals("Introduction to Programming", courses.get(0).getName());
            assertEquals(120, courses.get(0).getExamDurationMinutes());
        }

        @Test
        @DisplayName("Should use default duration when not specified")
        void shouldUseDefaultDurationWhenNotSpecified() throws IOException {
            File coursesFile = createTempFile("courses.csv",
                    "CourseCode,CourseName\n" +
                            "CS101,Programming\n");

            List<Course> courses = dataImportService.loadCourses(coursesFile);

            assertEquals(1, courses.size());
            assertEquals(120, courses.get(0).getExamDurationMinutes());
        }

        @Test
        @DisplayName("Should skip empty lines")
        void shouldSkipEmptyLines() throws IOException {
            File coursesFile = createTempFile("courses.csv",
                    "CourseCode,CourseName,DurationMinutes\n" +
                            "CS101,Programming,60\n" +
                            "\n" +
                            "CS102,Data Structures,90\n" +
                            "\n");

            List<Course> courses = dataImportService.loadCourses(coursesFile);

            assertEquals(2, courses.size());
        }

        @Test
        @DisplayName("Should skip duplicate course codes")
        void shouldSkipDuplicateCourseCodes() throws IOException {
            File coursesFile = createTempFile("courses.csv",
                    "CourseCode,CourseName,DurationMinutes\n" +
                            "CS101,Programming,60\n" +
                            "CS101,Programming 2,90\n" +
                            "CS102,Data Structures,60\n");

            List<Course> courses = dataImportService.loadCourses(coursesFile);

            assertEquals(2, courses.size());
            assertEquals("CS101", courses.get(0).getCode());
            assertEquals("Programming", courses.get(0).getName()); // First one wins
        }

        @Test
        @DisplayName("Should throw exception for empty file")
        void shouldThrowExceptionForEmptyFile() throws IOException {
            File coursesFile = createTempFile("courses.csv", "");

            assertThrows(IllegalArgumentException.class, () -> dataImportService.loadCourses(coursesFile));
        }

        @Test
        @DisplayName("Should handle single column format")
        void shouldHandleSingleColumnFormat() throws IOException {
            File coursesFile = createTempFile("courses.csv",
                    "ALL OF THE COURSES\n" +
                            "CS101\n" +
                            "CS102\n" +
                            "CS103\n");

            List<Course> courses = dataImportService.loadCourses(coursesFile);

            assertEquals(3, courses.size());
            assertEquals("CS101", courses.get(0).getCode());
            assertEquals("CS101", courses.get(0).getName()); // Name defaults to code
            assertEquals(120, courses.get(0).getExamDurationMinutes()); // Default duration
        }

        @Test
        @DisplayName("Should handle invalid duration gracefully")
        void shouldHandleInvalidDurationGracefully() throws IOException {
            File coursesFile = createTempFile("courses.csv",
                    "CourseCode,CourseName,DurationMinutes\n" +
                            "CS101,Programming,invalid\n");

            List<Course> courses = dataImportService.loadCourses(coursesFile);

            assertEquals(1, courses.size());
            assertEquals(120, courses.get(0).getExamDurationMinutes()); // Default used
        }
    }

    @Nested
    @DisplayName("Load Classrooms Tests")
    class LoadClassroomsTests {

        @Test
        @DisplayName("Should load classrooms from CSV with header")
        void shouldLoadClassroomsFromCsvWithHeader() throws IOException {
            File classroomsFile = createTempFile("classrooms.csv",
                    "RoomID,RoomName,Capacity\n" +
                            "A101,Lecture Hall A,150\n" +
                            "A102,Lecture Hall B,100\n" +
                            "B201,Lab 1,30\n");

            List<Classroom> classrooms = dataImportService.loadClassrooms(classroomsFile);

            assertEquals(3, classrooms.size());
            assertEquals("A101", classrooms.get(0).getId());
            assertEquals("Lecture Hall A", classrooms.get(0).getName());
            assertEquals(150, classrooms.get(0).getCapacity());
        }

        @Test
        @DisplayName("Should handle semicolon-separated format")
        void shouldHandleSemicolonSeparatedFormat() throws IOException {
            File classroomsFile = createTempFile("classrooms.csv",
                    "ALL OF THE CLASSROOMS\n" +
                            "RoomA;100\n" +
                            "RoomB;50\n" +
                            "RoomC;200\n");

            List<Classroom> classrooms = dataImportService.loadClassrooms(classroomsFile);

            assertEquals(3, classrooms.size());
            assertEquals("RoomA", classrooms.get(0).getId());
            assertEquals(100, classrooms.get(0).getCapacity());
        }

        @Test
        @DisplayName("Should skip invalid capacity")
        void shouldSkipInvalidCapacity() throws IOException {
            File classroomsFile = createTempFile("classrooms.csv",
                    "RoomID,RoomName,Capacity\n" +
                            "A101,Hall A,150\n" +
                            "A102,Hall B,invalid\n" +
                            "A103,Hall C,100\n");

            List<Classroom> classrooms = dataImportService.loadClassrooms(classroomsFile);

            assertEquals(2, classrooms.size());
        }

        @Test
        @DisplayName("Should skip zero capacity")
        void shouldSkipZeroCapacity() throws IOException {
            File classroomsFile = createTempFile("classrooms.csv",
                    "RoomID,RoomName,Capacity\n" +
                            "A101,Hall A,150\n" +
                            "A102,Hall B,0\n");

            List<Classroom> classrooms = dataImportService.loadClassrooms(classroomsFile);

            assertEquals(1, classrooms.size());
        }

        @Test
        @DisplayName("Should skip negative capacity")
        void shouldSkipNegativeCapacity() throws IOException {
            File classroomsFile = createTempFile("classrooms.csv",
                    "RoomID,RoomName,Capacity\n" +
                            "A101,Hall A,150\n" +
                            "A102,Hall B,-50\n");

            List<Classroom> classrooms = dataImportService.loadClassrooms(classroomsFile);

            assertEquals(1, classrooms.size());
        }

        @Test
        @DisplayName("Should skip duplicate room IDs")
        void shouldSkipDuplicateRoomIds() throws IOException {
            File classroomsFile = createTempFile("classrooms.csv",
                    "RoomID,RoomName,Capacity\n" +
                            "A101,Hall A,150\n" +
                            "A101,Hall A Duplicate,100\n" +
                            "A102,Hall B,50\n");

            List<Classroom> classrooms = dataImportService.loadClassrooms(classroomsFile);

            assertEquals(2, classrooms.size());
        }
    }

    @Nested
    @DisplayName("Load Students Tests")
    class LoadStudentsTests {

        @Test
        @DisplayName("Should load students from CSV with header")
        void shouldLoadStudentsFromCsvWithHeader() throws IOException {
            File studentsFile = createTempFile("students.csv",
                    "StudentID,StudentName\n" +
                            "2021001,Ali Yilmaz\n" +
                            "2021002,Ayse Demir\n");

            List<Student> students = dataImportService.loadStudents(studentsFile);

            assertEquals(2, students.size());
            assertEquals("2021001", students.get(0).getId());
            assertEquals("Ali Yilmaz", students.get(0).getName());
        }

        @Test
        @DisplayName("Should handle single column format")
        void shouldHandleSingleColumnFormat() throws IOException {
            File studentsFile = createTempFile("students.csv",
                    "ALL OF THE STUDENTS\n" +
                            "2021001\n" +
                            "2021002\n" +
                            "2021003\n");

            List<Student> students = dataImportService.loadStudents(studentsFile);

            assertEquals(3, students.size());
            assertEquals("2021001", students.get(0).getId());
            assertEquals("2021001", students.get(0).getName()); // Name defaults to ID
        }

        @Test
        @DisplayName("Should skip duplicate student IDs")
        void shouldSkipDuplicateStudentIds() throws IOException {
            File studentsFile = createTempFile("students.csv",
                    "StudentID,StudentName\n" +
                            "2021001,Ali\n" +
                            "2021001,Ali Duplicate\n" +
                            "2021002,Ayse\n");

            List<Student> students = dataImportService.loadStudents(studentsFile);

            assertEquals(2, students.size());
        }
    }

    @Nested
    @DisplayName("Load Attendance Tests")
    class LoadAttendanceTests {

        @Test
        @DisplayName("Should load attendance from CSV format")
        void shouldLoadAttendanceFromCsvFormat() throws IOException {
            List<Course> courses = List.of(
                    new Course("CS101", "Programming", 60),
                    new Course("CS102", "Data Structures", 60));
            List<Student> students = List.of(
                    new Student("S001", "Ali"),
                    new Student("S002", "Ayse"));

            File attendanceFile = createTempFile("attendance.csv",
                    "StudentID,StudentName,CourseCode\n" +
                            "S001,Ali,CS101\n" +
                            "S001,Ali,CS102\n" +
                            "S002,Ayse,CS101\n");

            List<Enrollment> enrollments = dataImportService.loadAttendance(attendanceFile, courses, students);

            assertEquals(3, enrollments.size());
        }

        @Test
        @DisplayName("Should load attendance from bracket list format")
        void shouldLoadAttendanceFromBracketListFormat() throws IOException {
            List<Course> courses = List.of(
                    new Course("CS101", "Programming", 60),
                    new Course("CS102", "Data Structures", 60));
            List<Student> students = new ArrayList<>(); // Will be created from file

            File attendanceFile = createTempFile("attendance.csv",
                    "ALL OF THE ATTENDANCE\n" +
                            "CS101\n" +
                            "['S001', 'S002', 'S003']\n" +
                            "CS102\n" +
                            "['S001', 'S004']\n");

            List<Enrollment> enrollments = dataImportService.loadAttendance(attendanceFile, courses, students);

            assertEquals(5, enrollments.size());
        }

        @Test
        @DisplayName("Should skip unknown course codes")
        void shouldSkipUnknownCourseCodes() throws IOException {
            List<Course> courses = List.of(
                    new Course("CS101", "Programming", 60));
            List<Student> students = List.of(
                    new Student("S001", "Ali"));

            File attendanceFile = createTempFile("attendance.csv",
                    "StudentID,StudentName,CourseCode\n" +
                            "S001,Ali,CS101\n" +
                            "S001,Ali,UNKNOWN999\n");

            List<Enrollment> enrollments = dataImportService.loadAttendance(attendanceFile, courses, students);

            assertEquals(1, enrollments.size());
        }

        @Test
        @DisplayName("Should create new students from attendance file")
        void shouldCreateNewStudentsFromAttendanceFile() throws IOException {
            List<Course> courses = List.of(
                    new Course("CS101", "Programming", 60));
            List<Student> students = new ArrayList<>(); // Empty, will be created

            File attendanceFile = createTempFile("attendance.csv",
                    "StudentID,StudentName,CourseCode\n" +
                            "NEWSTUDENT1,New Student Name,CS101\n");

            List<Enrollment> enrollments = dataImportService.loadAttendance(attendanceFile, courses, students);

            assertEquals(1, enrollments.size());
            assertEquals("NEWSTUDENT1", enrollments.get(0).getStudent().getId());
        }
    }

    @Nested
    @DisplayName("File Type Detection Tests")
    class FileTypeDetectionTests {

        @Test
        @DisplayName("Should reject wrong file type for courses")
        void shouldRejectWrongFileTypeForCourses() throws IOException {
            File classroomsFile = createTempFile("classrooms.csv",
                    "RoomID,RoomName,Capacity\n" +
                            "A101,Hall A,150\n");

            assertThrows(IllegalArgumentException.class,
                    () -> dataImportService.loadCourses(classroomsFile));
        }

        @Test
        @DisplayName("Should reject wrong file type for classrooms")
        void shouldRejectWrongFileTypeForClassrooms() throws IOException {
            File studentsFile = createTempFile("students.csv",
                    "StudentID,StudentName\n" +
                            "S001,Ali\n");

            assertThrows(IllegalArgumentException.class,
                    () -> dataImportService.loadClassrooms(studentsFile));
        }
    }

    // Helper method

    private File createTempFile(String name, String content) throws IOException {
        File file = tempDir.resolve(name).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }
}
