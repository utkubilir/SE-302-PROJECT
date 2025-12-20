package com.examplanner.services;

import com.examplanner.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConstraintChecker service.
 */
class ConstraintCheckerTest {

    private ConstraintChecker constraintChecker;
    private Course course1;
    private Course course2;
    private Student student1;
    private Classroom classroom1;
    private Classroom classroom2;

    @BeforeEach
    void setUp() {
        constraintChecker = new ConstraintChecker();
        constraintChecker.setMinGapMinutes(180); // 3 hours
        constraintChecker.setMaxExamsPerDay(2);

        course1 = new Course("CS101", "Programming", 120);
        course2 = new Course("CS102", "Data Structures", 90);
        student1 = new Student("S001", "Ali");
        classroom1 = new Classroom("A101", "Hall A", 100);
        classroom2 = new Classroom("A102", "Hall B", 50);
    }

    @Nested
    @DisplayName("Time Window Tests")
    class TimeWindowTests {

        @Test
        @DisplayName("Should accept exam within working hours")
        void shouldAcceptExamWithinWorkingHours() {
            ExamSlot slot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(9, 0),
                    LocalTime.of(11, 0));

            assertTrue(constraintChecker.isWithinTimeWindow(slot));
        }

        @Test
        @DisplayName("Should accept exam ending at 18:30")
        void shouldAcceptExamEndingAt1830() {
            ExamSlot slot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(16, 30),
                    LocalTime.of(18, 30));

            assertTrue(constraintChecker.isWithinTimeWindow(slot));
        }

        @Test
        @DisplayName("Should reject exam starting before 9:00")
        void shouldRejectExamStartingBefore9() {
            ExamSlot slot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(8, 0),
                    LocalTime.of(10, 0));

            assertFalse(constraintChecker.isWithinTimeWindow(slot));
        }

        @Test
        @DisplayName("Should reject exam ending after 18:30")
        void shouldRejectExamEndingAfter1830() {
            ExamSlot slot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(17, 0),
                    LocalTime.of(19, 0));

            assertFalse(constraintChecker.isWithinTimeWindow(slot));
        }
    }

    @Nested
    @DisplayName("Capacity Tests")
    class CapacityTests {

        @Test
        @DisplayName("Should accept when classroom has enough capacity")
        void shouldAcceptWhenCapacityIsSufficient() {
            List<Student> students = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                students.add(new Student("S" + i, "Student " + i));
            }

            Map<String, List<Student>> courseStudentsMap = Map.of(course1.getCode(), students);

            assertTrue(constraintChecker.fitsCapacity(classroom1, course1, courseStudentsMap));
        }

        @Test
        @DisplayName("Should reject when classroom is too small")
        void shouldRejectWhenCapacityIsInsufficient() {
            List<Student> students = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                students.add(new Student("S" + i, "Student " + i));
            }

            Map<String, List<Student>> courseStudentsMap = Map.of(course1.getCode(), students);

            assertFalse(constraintChecker.fitsCapacity(classroom2, course1, courseStudentsMap)); // classroom2 has 50
                                                                                                 // capacity
        }

        @Test
        @DisplayName("Should accept when enrollment count equals capacity")
        void shouldAcceptWhenEnrollmentEqualsCapacity() {
            List<Student> students = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                students.add(new Student("S" + i, "Student " + i));
            }

            Map<String, List<Student>> courseStudentsMap = Map.of(course1.getCode(), students);

            assertTrue(constraintChecker.fitsCapacity(classroom1, course1, courseStudentsMap)); // classroom1 has 100
                                                                                                // capacity
        }
    }

    @Nested
    @DisplayName("Classroom Availability Tests")
    class ClassroomAvailabilityTests {

        @Test
        @DisplayName("Should be available when no exams scheduled")
        void shouldBeAvailableWhenNoExamsScheduled() {
            ExamSlot slot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(9, 0),
                    LocalTime.of(11, 0));

            List<Exam> existingExams = new ArrayList<>();

            assertTrue(constraintChecker.isClassroomAvailable(classroom1, slot, existingExams));
        }

        @Test
        @DisplayName("Should be unavailable when overlapping exam exists")
        void shouldBeUnavailableWhenOverlappingExamExists() {
            ExamSlot existingSlot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(10, 0),
                    LocalTime.of(12, 0));
            Exam existingExam = new Exam(course1, classroom1, existingSlot);

            ExamSlot newSlot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(11, 0),
                    LocalTime.of(13, 0));

            List<Exam> existingExams = List.of(existingExam);

            assertFalse(constraintChecker.isClassroomAvailable(classroom1, newSlot, existingExams));
        }

        @Test
        @DisplayName("Should be available when exam is in different classroom")
        void shouldBeAvailableWhenExamInDifferentClassroom() {
            ExamSlot existingSlot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(10, 0),
                    LocalTime.of(12, 0));
            Exam existingExam = new Exam(course1, classroom2, existingSlot); // Different classroom

            ExamSlot newSlot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(10, 0),
                    LocalTime.of(12, 0));

            List<Exam> existingExams = List.of(existingExam);

            assertTrue(constraintChecker.isClassroomAvailable(classroom1, newSlot, existingExams));
        }

        @Test
        @DisplayName("Should be available for adjacent non-overlapping slots")
        void shouldBeAvailableForAdjacentSlots() {
            ExamSlot existingSlot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(9, 0),
                    LocalTime.of(11, 0));
            Exam existingExam = new Exam(course1, classroom1, existingSlot);

            ExamSlot newSlot = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(11, 0),
                    LocalTime.of(13, 0));

            List<Exam> existingExams = List.of(existingExam);

            assertTrue(constraintChecker.isClassroomAvailable(classroom1, newSlot, existingExams));
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should update min gap minutes")
        void shouldUpdateMinGapMinutes() {
            constraintChecker.setMinGapMinutes(60);
            // No exception means success - actual effect tested in integration
            assertDoesNotThrow(() -> constraintChecker.setMinGapMinutes(120));
        }

        @Test
        @DisplayName("Should update max exams per day")
        void shouldUpdateMaxExamsPerDay() {
            constraintChecker.setMaxExamsPerDay(3);
            // No exception means success
            assertDoesNotThrow(() -> constraintChecker.setMaxExamsPerDay(1));
        }
    }

    @Nested
    @DisplayName("Student Constraint Tests")
    class StudentConstraintTests {

        private ScheduleState scheduleState;
        private Map<String, List<Student>> courseStudentsMap;

        @BeforeEach
        void setUpState() {
            // Setup students
            List<Student> students = List.of(student1);
            courseStudentsMap = Map.of(
                    course1.getCode(), students,
                    course2.getCode(), students);

            scheduleState = new ScheduleState(courseStudentsMap);
        }

        @Test
        @DisplayName("Should detect daily limit violation")
        void shouldDetectDailyLimitViolation() {
            constraintChecker.setMaxExamsPerDay(2);

            // Add 2 exams for student1 on same day
            addExamToState(course1, LocalDate.of(2024, 12, 20), 9, 0, 11, 0);
            addExamToState(course2, LocalDate.of(2024, 12, 20), 12, 0, 14, 0);

            // Try to check a 3rd exam
            ExamSlot slot3 = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(15, 0),
                    LocalTime.of(17, 0));

            assertTrue(constraintChecker.violatesDailyLimit(student1, slot3, scheduleState));
        }

        @Test
        @DisplayName("Should allow exams within daily limit")
        void shouldAllowExamsWithinDailyLimit() {
            constraintChecker.setMaxExamsPerDay(2);

            // Add 1 exam
            addExamToState(course1, LocalDate.of(2024, 12, 20), 9, 0, 11, 0);

            // Check 2nd exam
            ExamSlot slot2 = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(12, 0),
                    LocalTime.of(14, 0));

            assertFalse(constraintChecker.violatesDailyLimit(student1, slot2, scheduleState));
        }

        @Test
        @DisplayName("Should detect immediate overlap as minimum gap violation")
        void shouldDetectImmediateOverlapAsGapViolation() {
            addExamToState(course1, LocalDate.of(2024, 12, 20), 9, 0, 11, 0);

            // Overlapping slot
            ExamSlot overlaps = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(10, 0),
                    LocalTime.of(12, 0));

            assertFalse(constraintChecker.hasMinimumGap(student1, overlaps, scheduleState));
        }

        @Test
        @DisplayName("Should detect violation when gap is insufficient")
        void shouldDetectViolationWhenGapInsufficient() {
            constraintChecker.setMinGapMinutes(60); // 1 hour gap required

            // Exam ends at 11:00
            addExamToState(course1, LocalDate.of(2024, 12, 20), 9, 0, 11, 0);

            // Next exam starts at 11:30 (30 min gap)
            ExamSlot shortGap = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(11, 30),
                    LocalTime.of(13, 30));

            assertFalse(constraintChecker.hasMinimumGap(student1, shortGap, scheduleState));
        }

        @Test
        @DisplayName("Should allow when gap is sufficient")
        void shouldAllowWhenGapSufficient() {
            constraintChecker.setMinGapMinutes(60);

            // Exam ends at 11:00
            addExamToState(course1, LocalDate.of(2024, 12, 20), 9, 0, 11, 0);

            // Next starts at 12:00 (60 min gap)
            ExamSlot sufficientGap = new ExamSlot(
                    LocalDate.of(2024, 12, 20),
                    LocalTime.of(12, 0),
                    LocalTime.of(14, 0));

            assertTrue(constraintChecker.hasMinimumGap(student1, sufficientGap, scheduleState));
        }

        private void addExamToState(Course course, LocalDate date, int h1, int m1, int h2, int m2) {
            ExamSlot slot = new ExamSlot(date, LocalTime.of(h1, m1), LocalTime.of(h2, m2));
            Exam exam = new Exam(course, classroom1, slot);
            scheduleState.add(exam);
        }
    }
}
