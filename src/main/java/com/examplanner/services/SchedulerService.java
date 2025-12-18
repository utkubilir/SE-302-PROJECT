package com.examplanner.services;

import com.examplanner.domain.Classroom;
import com.examplanner.domain.Course;
import com.examplanner.domain.Enrollment;
import com.examplanner.domain.Exam;
import com.examplanner.domain.ExamSlot;
import com.examplanner.domain.ExamTimetable;
import com.examplanner.domain.Student;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Exam Scheduler with backtracking and day compression. */
public class SchedulerService {

    private ConstraintChecker constraintChecker;

    // Pre-computed data structures for fast lookups
    private Map<String, List<Student>> courseStudentsMap;
    private Map<String, Integer> enrollmentCounts;
    private Map<String, Set<String>> studentCoursesMap; // studentId -> set of course codes

    public SchedulerService() {
        this.constraintChecker = new ConstraintChecker();
    }

    /** Generate optimized timetable with minimum days. */
    public ExamTimetable generateTimetable(List<Course> courses, List<Classroom> classrooms,
            List<Enrollment> enrollments, LocalDate startDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        // Default: 7 days from start date
        return generateTimetable(courses, classrooms, enrollments, startDate, startDate.plusDays(6));
    }

    public ExamTimetable generateTimetable(List<Course> courses, List<Classroom> classrooms,
            List<Enrollment> enrollments, LocalDate startDate, LocalDate endDate) {

        // === INPUT VALIDATION ===
        validateInputs(courses, classrooms, enrollments, startDate, endDate);

        if (endDate == null) {
            endDate = startDate.plusDays(6);
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        // Calculate available days from date range
        int maxDays = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
        System.out.println("\n=== SCHEDULER SERVICE: Optimized Backtracking with Day Compression ===");
        System.out.println("Date range: " + startDate + " to " + endDate + " (" + maxDays + " days)");
        System.out.println("Courses to schedule: " + courses.size());
        System.out.println("Available classrooms: " + classrooms.size());
        System.out.println("Total enrollments: " + enrollments.size());

        // Configure constraint checker
        long minGap = 180; // 180 mins (3h) per requirements
        constraintChecker.setMinGapMinutes(minGap);
        constraintChecker.setMaxExamsPerDay(2);
        System.out.println("Constraints: MinGap=" + minGap + "m, MaxExamsPerDay=2");

        // Build lookup maps
        buildLookupMaps(enrollments);

        // Sort courses by difficulty
        List<Course> sortedCourses = applySortingHeuristic(courses);
        System.out.println("\nCourses sorted by difficulty (students desc, duration desc):");
        for (int i = 0; i < Math.min(5, sortedCourses.size()); i++) {
            Course c = sortedCourses.get(i);
            System.out.println("  " + (i + 1) + ". " + c.getCode() +
                    " (students=" + enrollmentCounts.getOrDefault(c.getCode(), 0) +
                    ", duration=" + c.getExamDurationMinutes() + "min)");
        }
        if (sortedCourses.size() > 5) {
            System.out.println("  ... and " + (sortedCourses.size() - 5) + " more courses");
        }

        // Sort classrooms by capacity
        List<Classroom> sortedClassrooms = classrooms.stream()
                .sorted(Comparator.comparingInt(Classroom::getCapacity))
                .collect(Collectors.toList());

        // Generate time slots
        List<LocalTime> timeSlots = generateTimeSlots();
        System.out.println("Time slots available: " + timeSlots.size() + " slots (09:00 to 18:00, 30-min intervals)");

        // Calculate lower bound
        int minDaysNeeded = calculateMinDaysNeeded(courses, classrooms);
        int low = Math.max(1, minDaysNeeded);
        int high = maxDays;
        System.out.println("Heuristic lower bound: " + minDaysNeeded + " days");

        // Binary search for optimal days
        int optimalDays = -1;
        ExamTimetable bestResult = null;

        System.out.println("\nStarting binary search for optimal days (" + low + " - " + high + ")...");

        while (low <= high) {
            int mid = low + (high - low) / 2;
            System.out.println("\n>>> Trying " + mid + " day(s)...");

            ExamTimetable result = attemptSchedule(mid, sortedCourses, sortedClassrooms,
                    timeSlots, enrollments, startDate);

            if (result != null) {
                System.out.println(">>> SUCCESS with " + mid + " day(s)! Trying fewer...");
                optimalDays = mid;
                bestResult = result;
                high = mid - 1; // Try to minimize days
            } else {
                System.out.println(">>> FAILED with " + mid + " day(s)! Need more...");
                low = mid + 1;
            }
        }

        // Return result
        if (bestResult != null) {
            System.out.println("\n✓ OPTIMAL SCHEDULE FOUND: " + optimalDays + " day(s)");
            System.out.println("Total exams scheduled: " + bestResult.getExams().size());
            return bestResult;
        } else {
            throw new RuntimeException(
                    "Could not find a valid schedule within " + maxDays + " days. " +
                            "Constraints may be too tight. Try extending the date range or " +
                            "reducing the number of courses.");
        }
    }

    /** Generate timetable with optimal + alternative options. */
    public ScheduleOptions generateTimetableWithOptions(List<Course> courses, List<Classroom> classrooms,
            List<Enrollment> enrollments, LocalDate startDate, LocalDate endDate) {

        // Validate inputs
        validateInputs(courses, classrooms, enrollments, startDate, endDate);

        if (endDate == null) {
            endDate = startDate.plusDays(6);
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        int maxDays = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
        System.out.println("\n=== GENERATING SCHEDULE OPTIONS ===");
        System.out.println("Date range: " + startDate + " to " + endDate + " (" + maxDays + " days)");

        // Configure constraint checker
        long minGap = 180;
        constraintChecker.setMinGapMinutes(minGap);
        constraintChecker.setMaxExamsPerDay(2);

        // Build lookup maps
        buildLookupMaps(enrollments);

        // Sort courses and classrooms
        List<Course> sortedCourses = applySortingHeuristic(courses);
        List<Classroom> sortedClassrooms = classrooms.stream()
                .sorted(Comparator.comparingInt(Classroom::getCapacity))
                .collect(Collectors.toList());
        List<LocalTime> timeSlots = generateTimeSlots();

        // Find optimal (minimum) days first
        int minDaysNeeded = calculateMinDaysNeeded(courses, classrooms);
        int low = Math.max(1, minDaysNeeded);
        int high = maxDays;
        int optimalDays = -1;
        ExamTimetable optimalSchedule = null;

        System.out.println("Finding optimal schedule...");

        // Binary search for optimal
        while (low <= high) {
            int mid = low + (high - low) / 2;
            ExamTimetable result = attemptSchedule(mid, sortedCourses, sortedClassrooms,
                    timeSlots, enrollments, startDate);

            if (result != null) {
                optimalDays = mid;
                optimalSchedule = result;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        if (optimalSchedule == null) {
            throw new RuntimeException(
                    "Could not find a valid schedule within " + maxDays + " days. " +
                            "Constraints may be too tight. Try extending the date range.");
        }

        System.out.println("\n✓ OPTIMAL: " + optimalDays + " day(s)");

        // Create options container
        ScheduleOptions options = new ScheduleOptions(optimalDays, optimalSchedule);
        options.addOption(optimalDays, optimalSchedule);

        // Generate alternative schedules (optimal + 1, +2, +3, +4 days if within range)
        System.out.println("\nGenerating alternative schedules...");
        for (int extraDays = 1; extraDays <= 4; extraDays++) {
            int altDays = optimalDays + extraDays;
            if (altDays > maxDays) {
                break; // Don't exceed user's date range
            }

            ExamTimetable altSchedule = attemptSchedule(altDays, sortedCourses, sortedClassrooms,
                    timeSlots, enrollments, startDate);

            if (altSchedule != null) {
                options.addOption(altDays, altSchedule);
                System.out.println("  Generated " + altDays + "-day alternative");
            }
        }

        System.out.println("\n✓ Generated " + options.getAllOptions().size() + " schedule option(s)");
        return options;
    }

    /** Validate inputs. */
    private void validateInputs(List<Course> courses, List<Classroom> classrooms,
            List<Enrollment> enrollments, LocalDate startDate, LocalDate endDate) {
        if (courses == null || courses.isEmpty()) {
            throw new IllegalArgumentException("Courses list cannot be null or empty");
        }
        if (classrooms == null || classrooms.isEmpty()) {
            throw new IllegalArgumentException("Classrooms list cannot be null or empty");
        }
        if (enrollments == null || enrollments.isEmpty()) {
            throw new IllegalArgumentException("Enrollments list cannot be null or empty");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        if (startDate.isBefore(LocalDate.now())) {
            System.out.println("Warning: Start date is in the past, scheduling may include past dates");
        }
    }

    /** Build lookup maps for fast constraint checking. */
    private void buildLookupMaps(List<Enrollment> enrollments) {
        System.out.println("\nBuilding lookup maps...");

        // Map: courseCode -> List<Student>
        courseStudentsMap = enrollments.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCourse().getCode(),
                        Collectors.mapping(Enrollment::getStudent, Collectors.toList())));

        // Map: courseCode -> student count
        enrollmentCounts = new HashMap<>();
        for (Map.Entry<String, List<Student>> entry : courseStudentsMap.entrySet()) {
            enrollmentCounts.put(entry.getKey(), entry.getValue().size());
        }

        // Map: studentId -> Set<courseCode>
        studentCoursesMap = new HashMap<>();
        for (Enrollment e : enrollments) {
            String studentId = e.getStudent().getId();
            String courseCode = e.getCourse().getCode();
            studentCoursesMap.computeIfAbsent(studentId, k -> new HashSet<>()).add(courseCode);
        }

        System.out.println("  Courses with enrollments: " + courseStudentsMap.size());
        System.out.println("  Unique students: " + studentCoursesMap.size());
    }

    /** Sort courses by difficulty (students desc, duration desc). */
    private List<Course> applySortingHeuristic(List<Course> courses) {
        List<Course> sorted = new ArrayList<>(courses);
        sorted.sort(Comparator
                .comparingInt((Course c) -> enrollmentCounts.getOrDefault(c.getCode(), 0))
                .reversed()
                .thenComparingInt(Course::getExamDurationMinutes)
                .reversed());
        return sorted;
    }

    /** Generate 30-min time slots from 09:00 to 18:00. */
    private List<LocalTime> generateTimeSlots() {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime start = LocalTime.of(9, 0);

        // We need to account for exam duration when determining valid start times.
        // For now, generate all 30-min intervals. Validity is checked during
        // scheduling.
        while (!start.isAfter(LocalTime.of(18, 0))) {
            slots.add(start);
            start = start.plusMinutes(30);
        }
        return slots;
    }

    /** Calculate minimum days needed based on capacity and student load. */
    private int calculateMinDaysNeeded(List<Course> courses, List<Classroom> classrooms) {
        // 1. Capacity constraint: total exam minutes / (classrooms * daily minutes)
        double totalExamMinutes = courses.stream().mapToDouble(Course::getExamDurationMinutes).sum();
        double dailyClassroomMinutes = classrooms.size() * 570.0; // 9:00-18:30 = 570 mins
        int minDaysForCapacity = (int) Math.ceil(totalExamMinutes / dailyClassroomMinutes);

        // 2. Student load constraint: max exams per student / 2 (max exams per day)
        long maxExamsForStudent = studentCoursesMap.values().stream()
                .mapToLong(Set::size)
                .max()
                .orElse(0);
        int minDaysForStudents = (int) Math.ceil((double) maxExamsForStudent / 2.0);

        return Math.max(minDaysForCapacity, minDaysForStudents);
    }

    /** Try to schedule all exams within given days. */
    private ExamTimetable attemptSchedule(int days, List<Course> sortedCourses,
            List<Classroom> sortedClassrooms, List<LocalTime> timeSlots,
            List<Enrollment> enrollments, LocalDate startDate) {

        // Initialize schedule state
        ScheduleState state = new ScheduleState(courseStudentsMap);

        // Track classroom usage for better distribution
        Map<String, Integer> classroomUsageCount = new HashMap<>();
        for (Classroom c : sortedClassrooms) {
            classroomUsageCount.put(c.getId(), 0);
        }

        // Timeout configuration - 30 seconds per attempt for complex schedules
        long startTime = System.currentTimeMillis();
        long timeoutMs = 30000; // 30 seconds per attempt

        // Start backtracking
        boolean success = backtrack(0, sortedCourses, state, sortedClassrooms,
                timeSlots, startDate, days, startTime, timeoutMs, classroomUsageCount);

        long elapsed = System.currentTimeMillis() - startTime;

        if (success) {
            System.out.println("  Scheduled " + state.getExams().size() + " exams in " + elapsed + "ms");
            return new ExamTimetable(new ArrayList<>(state.getExams()), enrollments);
        }

        System.out.println("  Failed after " + elapsed + "ms");
        return null;
    }

    /** Recursive backtracking with day compression. */
    private boolean backtrack(int index, List<Course> courses, ScheduleState state,
            List<Classroom> classrooms, List<LocalTime> timeSlots,
            LocalDate startDate, int maxDays, long startTime, long timeoutMs,
            Map<String, Integer> classroomUsageCount) {

        // Check timeout
        if (System.currentTimeMillis() - startTime > timeoutMs) {
            return false;
        }

        // Base case: all courses scheduled
        if (index == courses.size()) {
            return true;
        }

        Course course = courses.get(index);
        int studentCount = enrollmentCounts.getOrDefault(course.getCode(), 0);
        List<Student> students = courseStudentsMap.getOrDefault(course.getCode(), List.of());

        // Try earliest days first
        for (int dayOffset = 0; dayOffset < maxDays; dayOffset++) {
            LocalDate date = startDate.plusDays(dayOffset);

            // Skip if any student has max exams today
            if (anyStudentHasMaxExamsOnDay(students, date, state)) {
                continue;
            }

            // Find valid time slots for this day
            List<LocalTime> validSlots = new ArrayList<>();
            for (LocalTime slotStart : timeSlots) {
                LocalTime slotEnd = slotStart.plusMinutes(course.getExamDurationMinutes());

                // Check if exam fits within time window (ends by 18:30)
                if (slotEnd.isAfter(LocalTime.of(18, 30))) {
                    continue;
                }

                ExamSlot slot = new ExamSlot(date, slotStart, slotEnd);

                // Quick check: Can ANY student take this exam at this time?
                boolean canPlaceHere = true;
                for (Student s : students) {
                    List<Exam> studentExamsToday = state.getExamsForStudentDate(s.getId(), date);
                    for (Exam existing : studentExamsToday) {
                        // Check 3-hour gap requirement
                        long gapMinutes = calculateGapMinutes(existing.getSlot(), slot);
                        if (gapMinutes < 180) { // 3 hours = 180 minutes
                            canPlaceHere = false;
                            break;
                        }
                    }
                    if (!canPlaceHere)
                        break;
                }

                if (canPlaceHere) {
                    validSlots.add(slotStart);
                }
            }

            // Try valid time slots
            for (LocalTime slotStart : validSlots) {
                LocalTime slotEnd = slotStart.plusMinutes(course.getExamDurationMinutes());
                ExamSlot slot = new ExamSlot(date, slotStart, slotEnd);

                // Pick least-used classroom
                List<Classroom> suitableClassrooms = classrooms.stream()
                        .filter(c -> studentCount <= c.getCapacity())
                        .sorted(Comparator.comparingInt(c -> classroomUsageCount.getOrDefault(c.getId(), 0)))
                        .collect(Collectors.toList());

                for (Classroom classroom : suitableClassrooms) {
                    Exam candidate = new Exam(course, classroom, slot);

                    // Full constraint check via ConstraintChecker
                    if (constraintChecker.checkAll(candidate, state)) {
                        // Place the exam and update usage counter
                        state.add(candidate);
                        classroomUsageCount.merge(classroom.getId(), 1, Integer::sum);

                        // Recurse to next course
                        if (backtrack(index + 1, courses, state, classrooms,
                                timeSlots, startDate, maxDays, startTime, timeoutMs, classroomUsageCount)) {
                            return true;
                        }

                        // Backtrack: remove the exam and decrement usage counter
                        state.removeLast();
                        classroomUsageCount.merge(classroom.getId(), -1, Integer::sum);
                    }
                }
            }
        }

        // No valid placement found for this course
        return false;
    }

    /** Get gap in minutes between two slots. */
    private long calculateGapMinutes(ExamSlot slot1, ExamSlot slot2) {
        // If different days, return large number (no conflict)
        if (!slot1.getDate().equals(slot2.getDate())) {
            return Long.MAX_VALUE;
        }

        LocalTime end1 = slot1.getEndTime();
        LocalTime start1 = slot1.getStartTime();
        LocalTime end2 = slot2.getEndTime();
        LocalTime start2 = slot2.getStartTime();

        // Calculate gap between the two slots
        if (end1.isBefore(start2) || end1.equals(start2)) {
            // slot1 ends before slot2 starts
            return java.time.Duration.between(end1, start2).toMinutes();
        } else if (end2.isBefore(start1) || end2.equals(start1)) {
            // slot2 ends before slot1 starts
            return java.time.Duration.between(end2, start1).toMinutes();
        } else {
            // Overlapping slots
            return 0;
        }
    }

    /** Check if any student has max exams on this day. */
    private boolean anyStudentHasMaxExamsOnDay(List<Student> students, LocalDate date, ScheduleState state) {
        for (Student s : students) {
            if (state.getExamsCountForStudentDate(s.getId(), date) >= 2) {
                return true;
            }
        }
        return false;
    }
}
