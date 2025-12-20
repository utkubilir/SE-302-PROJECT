package com.examplanner.services;

import com.examplanner.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScheduleState logic.
 */
class ScheduleStateTest {

    private ScheduleState scheduleState;
    private Course course1;
    private Student student1;
    private Classroom classroom1;

    @BeforeEach
    void setUp() {
        course1 = new Course("CS101", "Intro", 120);
        student1 = new Student("S1", "Ali");
        classroom1 = new Classroom("A101", "Hall", 50);

        Map<String, List<Student>> courseStudents = Map.of(course1.getCode(), List.of(student1));
        scheduleState = new ScheduleState(courseStudents);
    }

    @Test
    @DisplayName("Should correctly track student exam counts")
    void shouldTrackStudentExamCounts() {
        LocalDate date = LocalDate.of(2024, 1, 1);
        ExamSlot slot = new ExamSlot(date, LocalTime.now(), LocalTime.now().plusHours(2));
        Exam exam = new Exam(course1, classroom1, slot);

        assertEquals(0, scheduleState.getExamsCountForStudentDate(student1.getId(), date));

        scheduleState.add(exam);
        assertEquals(1, scheduleState.getExamsCountForStudentDate(student1.getId(), date));

        scheduleState.removeLast();
        assertEquals(0, scheduleState.getExamsCountForStudentDate(student1.getId(), date));
    }

    @Test
    @DisplayName("Should correctly track classroom availability")
    void shouldTrackClassroomAvailability() {
        LocalDate date = LocalDate.of(2024, 1, 1);
        ExamSlot slot = new ExamSlot(date, LocalTime.of(10, 0), LocalTime.of(12, 0));
        Exam exam = new Exam(course1, classroom1, slot);

        scheduleState.add(exam);

        ExamSlot overlappingSlot = new ExamSlot(date, LocalTime.of(11, 0), LocalTime.of(13, 0));
        assertFalse(scheduleState.isClassroomAvailable(classroom1.getId(), overlappingSlot));

        ExamSlot nonOverlappingSlot = new ExamSlot(date, LocalTime.of(12, 0), LocalTime.of(14, 0));
        assertTrue(scheduleState.isClassroomAvailable(classroom1.getId(), nonOverlappingSlot));
    }

    @Test
    @DisplayName("Should return empty lists for unknown lookups")
    void shouldReturnEmptyListsForUnknownLookups() {
        LocalDate date = LocalDate.of(2024, 1, 1);

        List<Exam> exams = scheduleState.getExamsForStudentDate("UNKNOWN_STUDENT", date);
        assertNotNull(exams);
        assertTrue(exams.isEmpty());

        int count = scheduleState.getExamsCountForStudentDate("UNKNOWN_STUDENT", date);
        assertEquals(0, count);
    }
}
