package com.examplanner.services;

import com.examplanner.domain.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SchedulerService {

    private ConstraintChecker constraintChecker;

    public SchedulerService() {
        this.constraintChecker = new ConstraintChecker();
    }

    public ExamTimetable generateTimetable(List<Course> courses, List<Classroom> classrooms,
                                           List<Enrollment> enrollments, LocalDate startDate, boolean useStrictConstraints,
                                           List<LocalDate> blackoutDates) {

        long minGap = useStrictConstraints ? 120 : 30;

        System.out.println("Applying Constraints: MinGap=" + minGap + "m, MaxExams=2");
        constraintChecker.setMinGapMinutes(minGap);
        constraintChecker.setMaxExamsPerDay(2);

        System.out.println("\n=== SCHEDULER SERVICE: Starting generation ===");
        System.out.println("Courses to schedule: " + courses.size());
        System.out.println("Available classrooms: " + classrooms.size());
        System.out.println("Total enrollments: " + enrollments.size());

        // Sort courses by difficulty (number of students enrolled)
        List<Course> sortedCourses = new ArrayList<>(courses);
        sortedCourses.sort(Comparator.comparingInt((Course c) -> getStudentCount(c, enrollments)).reversed());

        // Calculate a smart lower bound for the number of days
        int days = calculateMinDaysNeeded(courses, classrooms, enrollments);
        System.out.println("Computed heuristic starting days: " + days);

        // Adjust max attempts based on problem size
        int maxAttemptsPerDay = courses.size() * classrooms.size() * 2000;
        System.out.println("Max attempts per day configuration: " + maxAttemptsPerDay);

        // OPTIMIZATION: Sort Classrooms by Capacity ASC (Best Fit Heuristic)
        List<Classroom> sortedClassrooms = new ArrayList<>(classrooms);
        sortedClassrooms.sort(Comparator.comparingInt(Classroom::getCapacity));

        // Pre-compute lookup maps for optimization
        Map<String, List<Student>> courseStudentsMap = enrollments.stream()
                .collect(Collectors.groupingBy(e -> e.getCourse().getCode(),
                        Collectors.mapping(Enrollment::getStudent, Collectors.toList())));

        int low = days; // Heuristic lower bound
        int high = 50; // Safe upper bound
        int optimalDays = -1;
        ExamTimetable bestResult = null;

        System.out.println("Starting Binary Search for optimal days (" + low + " - " + high + ")...");

        while (low <= high) {
            int mid = low + (high - low) / 2;
            System.out.println("\n>>> Trying " + mid + " days (Range: " + low + " - " + high + ")");

            ExamTimetable result = attemptSchedule(mid, sortedCourses, sortedClassrooms, enrollments, startDate,
                    maxAttemptsPerDay, courseStudentsMap, blackoutDates);

            if (result != null) {
                System.out.println(">>> Success with " + mid + " days! Trying fewer...");
                optimalDays = mid;
                bestResult = result;
                high = mid - 1; // Try to minimize days
            } else {
                System.out.println(">>> Failed (or timed out) with " + mid + " days! Need more...");
                low = mid + 1;
            }
        }

        if (bestResult != null) {
            System.out.println("\n✓ OPTIMAL SCHEDULE FOUND: " + optimalDays + " days.");
            return bestResult;
        } else {
            throw new RuntimeException("Could not find a schedule even with " + 50 + " days.");
        }
    }

    private ExamTimetable attemptSchedule(int days, List<Course> sortedCourses, List<Classroom> classrooms,
                                          List<Enrollment> enrollments, LocalDate startDate, int maxAttemptsPerDay,
                                          Map<String, List<Student>> courseStudentsMap,
                                          List<LocalDate> blackoutDates) {

        System.out.println("=== ATTEMPTING SCHEDULE WITH " + days + " DAY(S) ===");

        ScheduleState state = new ScheduleState(courseStudentsMap);

        long startTime = System.currentTimeMillis();
        long timeoutMs = 5000; // 5 seconds timeout per attempt

        AtomicInteger attemptCounter = new AtomicInteger(0);

        boolean success = backtrackWithLimit(0, sortedCourses, state, classrooms, startDate, days,
                maxAttemptsPerDay, attemptCounter, startTime, timeoutMs, blackoutDates);
        long elapsed = System.currentTimeMillis() - startTime;

        if (success) {
            System.out.println("✓ SUCCESS! Scheduled all exams in " + days + " day(s)");
            System.out.println("Total exams scheduled: " + state.getExams().size());
            System.out.println("Time taken: " + elapsed + "ms");
            return new ExamTimetable(new ArrayList<>(state.getExams()), enrollments);
        }

        System.out.println("✗ Failed (or Timed Out) with " + days + " day(s) (tried for " + elapsed + "ms)");
        return null;
    }

    private boolean backtrackWithLimit(int index, List<Course> courses, ScheduleState state,
                                       List<Classroom> classrooms,
                                       LocalDate startDate, int maxDays, int maxAttempts,
                                       AtomicInteger attemptCounter,
                                       long startTime, long timeoutMs,
                                       List<LocalDate> blackoutDates) {

        if (System.currentTimeMillis() - startTime > timeoutMs) {
            if (attemptCounter.get() % 5000 == 0)
                System.out.println("  [TIMEOUT reached after " + timeoutMs + "ms]");
            return false;
        }

        if (index == courses.size()) {
            System.out.println("✓ All " + courses.size() + " courses successfully scheduled!");
            return true;
        }

        if (attemptCounter.get() >= maxAttempts) {
            if (attemptCounter.get() % 10000 == 0) {
                System.out.println("  [Reached max attempts limit (" + maxAttempts + ") for " + maxDays + " day(s)]");
            }
            return false;
        }

        Course course = courses.get(index);
        attemptCounter.incrementAndGet();

        for (int d = 0; d < maxDays; d++) {
            LocalDate date = startDate.plusDays(d);

            if (blackoutDates != null && blackoutDates.contains(date)) {
                continue;
            }

            for (Classroom classroom : classrooms) {
                // Capacity check
                int size = state.getStudentsForCourse(course.getCode()).size();
                if (size > classroom.getCapacity()) {
                    continue;
                }

                LocalTime startTimeSlot = LocalTime.of(9, 0);
                LocalTime maxStart = LocalTime.of(18, 30).minusMinutes(course.getExamDurationMinutes());

                while (!startTimeSlot.isAfter(maxStart)) {
                    LocalTime endTime = startTimeSlot.plusMinutes(course.getExamDurationMinutes());
                    ExamSlot slot = new ExamSlot(date, startTimeSlot, endTime);
                    Exam candidate = new Exam(course, classroom, slot);

                    if (constraintChecker.checkAll(candidate, state)) {
                        state.add(candidate);

                        if (backtrackWithLimit(index + 1, courses, state, classrooms, startDate,
                                maxDays, maxAttempts, attemptCounter, startTime, timeoutMs, blackoutDates)) {
                            return true;
                        }

                        state.removeLast(); // Backtrack
                    }
                    startTimeSlot = startTimeSlot.plusMinutes(30);
                }
            }
        }
        return false;
    }

    private int getStudentCount(Course course, List<Enrollment> enrollments) {
        return (int) enrollments.stream().filter(e -> e.getCourse().getCode().equals(course.getCode())).count();
    }

    private int calculateMinDaysNeeded(List<Course> courses, List<Classroom> classrooms, List<Enrollment> enrollments) {
        double totalExamMinutes = courses.stream().mapToDouble(Course::getExamDurationMinutes).sum();
        double dailyClassroomMinutes = classrooms.size() * 570.0;
        int minDaysForCapacity = (int) Math.ceil(totalExamMinutes / dailyClassroomMinutes);

        Map<String, Long> examsPerStudent = enrollments.stream()
                .collect(Collectors.groupingBy(e -> e.getStudent().getId(), Collectors.counting()));

        long maxExamsForSingleStudent = examsPerStudent.values().stream().mapToLong(l -> l).max().orElse(0);
        int minDaysForStudents = (int) Math.ceil((double) maxExamsForSingleStudent / 2.0);

        int minDays = Math.max(minDaysForCapacity, minDaysForStudents);

        return Math.max(1, minDays + 1);
    }
}