package com.examplanner.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudentTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create student with valid parameters")
        void shouldCreateStudentWithValidParameters() {
            Student student = new Student("S1", "Ali");

            assertEquals("S1", student.getId());
            assertEquals("Ali", student.getName());
        }

        @Test
        @DisplayName("Should throw exception for null id")
        void shouldThrowExceptionForNullId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Student(null, "Ali"));
        }

        @Test
        @DisplayName("Should throw exception for empty id")
        void shouldThrowExceptionForEmptyId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Student("", "Ali"));
        }

        @Test
        @DisplayName("Should throw exception for null name")
        void shouldThrowExceptionForNullName() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Student("S1", null));
        }

        @Test
        @DisplayName("Should throw exception for empty name")
        void shouldThrowExceptionForEmptyName() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Student("S1", ""));
        }
    }
}
