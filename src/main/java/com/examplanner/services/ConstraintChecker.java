package com.examplanner.services;

import com.examplanner.domain.Classroom;
import com.examplanner.domain.Course;
import com.examplanner.domain.Enrollment;
import com.examplanner.domain.Exam;
import com.examplanner.domain.ExamSlot;
import com.examplanner.domain.Student;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.ResourceBundle;
import java.text.MessageFormat;

public class ConstraintChecker {

    private static final LocalTime MIN_START_TIME = LocalTime.of(9, 0);
    private static final LocalTime MAX_END_TIME = LocalTime.of(18, 30);
    private long minGapMinutes = 180; // Default 180 mins (3 hours)
    private int maxExamsPerDay = 2; // Default 2

    public void setMinGapMinutes(long minGapMinutes) {
        this.minGapMinutes = minGapMinutes;
    }

    public void setMaxExamsPerDay(int maxExamsPerDay) {
        this.maxExamsPerDay = maxExamsPerDay;
    }

    // Fast lookup maps

    public boolean checkAll(Exam candidateExam, ScheduleState state) {
        if (!isWithinTimeWindow(candidateExam.getSlot()))
            return false;

        // Capacity Logic
        List<Student> students = state.getStudentsForCourse(candidateExam.getCourse().getCode());
        int studentCount = students.size();

        if (studentCount > candidateExam.getClassroom().getCapacity())
            return false;

        // Check Classroom availability
        if (!isClassroomAvailable(candidateExam.getClassroom(), candidateExam.getSlot(), state.getExams()))
            return false;

        // Student constraints
        for (Student s : students) {
            if (violatesDailyLimit(s, candidateExam.getSlot(), state))
                return false;
            if (!hasMinimumGap(s, candidateExam.getSlot(), state))
                return false;
        }

        return true;
    }

    /**
     * Validates a manual move from the UI.
     * Returns a string error message if invalid, or null if valid.
     */
    public String checkManualMove(Exam newExam, List<Exam> allExams, List<Enrollment> enrollments,
            ResourceBundle bundle) {
        // 1. Time Window
        if (!isWithinTimeWindow(newExam.getSlot())) {
            return bundle.getString("validation.error.timeWindow");
        }

        // 2. Classroom Conflict (exclude the exam itself from the check if it's already
        // in list,
        // but here 'newExam' is a copy or modified instance, so we check against
        // others)
        for (Exam other : allExams) {
            if (other == newExam)
                continue; // Skip self if same instance
            if (other.getCourse().getCode().equals(newExam.getCourse().getCode()))
                continue; // Skip self if we are moving it

            if (other.getClassroom().getId().equals(newExam.getClassroom().getId())) {
                if (other.getSlot().overlaps(newExam.getSlot())) {
                    return MessageFormat.format(bundle.getString("validation.error.occupied"),
                            newExam.getClassroom().getName(), other.getCourse().getCode());
                }
            }
        }

        // 3. Student Constraints
        // Gather students for this course
        List<Student> students = enrollments.stream()
                .filter(e -> e.getCourse().getCode().equals(newExam.getCourse().getCode()))
                .map(Enrollment::getStudent)
                .collect(Collectors.toList());

        // Capacity
        if (students.size() > newExam.getClassroom().getCapacity()) {
            return MessageFormat.format(bundle.getString("validation.error.capacity"),
                    students.size(), newExam.getClassroom().getCapacity());
        }

        // Build a temporary lookup for students for the target date
        // optimizing this might be needed for large data, but for UI action it is
        // acceptable
        LocalDate targetDate = newExam.getSlot().getDate();

        for (Student s : students) {
            // Find other exams for this student on this day
            List<Exam> studentExamsOnDay = allExams.stream()
                    .filter(e -> !e.getCourse().getCode().equals(newExam.getCourse().getCode())) // Exclude the exam
                                                                                                 // being moved
                    .filter(e -> e.getSlot().getDate().equals(targetDate))
                    .filter(e -> {
                        // Check enrollment
                        return enrollments.stream().anyMatch(en -> en.getStudent().getId().equals(s.getId())
                                && en.getCourse().getCode().equals(e.getCourse().getCode()));
                    })
                    .collect(Collectors.toList());

            // Check Max Exams
            if (studentExamsOnDay.size() >= maxExamsPerDay) {
                return MessageFormat.format(bundle.getString("validation.error.studentLimit"),
                        s.getName(), studentExamsOnDay.size(), targetDate);
            }

            // Check Gap
            for (Exam existing : studentExamsOnDay) {
                long gap1 = Duration.between(existing.getSlot().getEndTime(), newExam.getSlot().getStartTime())
                        .toMinutes();
                long gap2 = Duration.between(newExam.getSlot().getEndTime(), existing.getSlot().getStartTime())
                        .toMinutes();

                // If gap is negative, it means overlap or wrong order.
                // Duration.between(A, B) is positive if A < B.

                if (existing.getSlot().overlaps(newExam.getSlot())) {
                    return MessageFormat.format(bundle.getString("validation.error.conflict"),
                            s.getName(), existing.getCourse().getCode());
                }

                // if existing ends Before new starts: gap1 > 0
                if (gap1 >= 0 && gap1 < minGapMinutes) {
                    return MessageFormat.format(bundle.getString("validation.error.gapAfter"),
                            gap1, s.getName(), existing.getCourse().getCode());
                }
                // if existing starts After new ends: gap2 > 0
                if (gap2 >= 0 && gap2 < minGapMinutes) {
                    return MessageFormat.format(bundle.getString("validation.error.gapBefore"),
                            gap2, s.getName(), existing.getCourse().getCode());
                }
            }
        }

        return null; // Valid
    }

    public boolean isWithinTimeWindow(ExamSlot slot) {
        return !slot.getStartTime().isBefore(MIN_START_TIME) && !slot.getEndTime().isAfter(MAX_END_TIME);
    }

    public boolean fitsCapacity(Classroom classroom, Course course, Map<String, List<Student>> courseStudentsMap) {
        int studentCount = courseStudentsMap.getOrDefault(course.getCode(), List.of()).size();
        return studentCount <= classroom.getCapacity();
    }

    public boolean isClassroomAvailable(Classroom classroom, ExamSlot slot, List<Exam> existingExams) {
        for (Exam exam : existingExams) {
            if (exam.getClassroom().getId().equals(classroom.getId())) {
                if (exam.getSlot().overlaps(slot)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean violatesDailyLimit(Student student, ExamSlot slot, ScheduleState state) {
        // O(1) Lookup
        int count = state.getExamsCountForStudentDate(student.getId(), slot.getDate());
        // If they already have 2, they can't have another (making it 3)
        // Adjust limit here: if limit is 2 exams per day.
        return count >= maxExamsPerDay;
    }

    public boolean hasMinimumGap(Student student, ExamSlot slot, ScheduleState state) {
        // O(K) where K is number of exams that student has ON THAT DAY (usually 0, 1,
        // or 2)
        List<Exam> dayExams = state.getExamsForStudentDate(student.getId(), slot.getDate());

        for (Exam existing : dayExams) {
            // First check for overlap - if slots overlap, it's an immediate conflict
            if (existing.getSlot().overlaps(slot)) {
                return false;
            }

            long gap1 = Duration.between(existing.getSlot().getEndTime(), slot.getStartTime()).toMinutes();
            long gap2 = Duration.between(slot.getEndTime(), existing.getSlot().getStartTime()).toMinutes();

            if (gap1 >= 0 && gap1 < minGapMinutes)
                return false;
            if (gap2 >= 0 && gap2 < minGapMinutes)
                return false;
        }
        return true;
    }

}
