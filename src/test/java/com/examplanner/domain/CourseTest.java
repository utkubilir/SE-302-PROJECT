package com.examplanner.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CourseTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create course with valid parameters")
        void shouldCreateCourseWithValidParameters() {
            Course course = new Course("CS101", "Introduction", 60);

            assertEquals("CS101", course.getCode());
            assertEquals("Introduction", course.getName());
            assertEquals(60, course.getExamDurationMinutes());
        }

        @Test
        @DisplayName("Should throw exception for null code")
        void shouldThrowExceptionForNullCode() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Course(null, "Name", 60));
        }

        @Test
        @DisplayName("Should throw exception for empty code")
        void shouldThrowExceptionForEmptyCode() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Course("", "Name", 60));
        }

        @Test
        @DisplayName("Should throw exception for null name")
        void shouldThrowExceptionForNullName() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Course("Code", null, 60));
        }

        @Test
        @DisplayName("Should throw exception for zero duration")
        void shouldThrowExceptionForZeroDuration() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Course("Code", "Name", 0));
        }

        @Test
        @DisplayName("Should throw exception for negative duration")
        void shouldThrowExceptionForNegativeDuration() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Course("Code", "Name", -1));
        }
    }

    @Test
    @DisplayName("Should match code in toString if name is same")
    void shouldMatchCodeInToString() {
        Course course = new Course("CS101", "CS101", 60);
        assertEquals("CS101", course.toString());
    }
}
