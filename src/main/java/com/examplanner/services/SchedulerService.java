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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exam Scheduler Algorithm with Backtracking
 * 
 * Kurallar (hepsi zorunlu):
 * 1) Bir öğrenci aynı anda iki sınava giremez.
 * 2) Bir öğrenci aynı gün en fazla 2 sınava girsin.
 * 3) Bir öğrenci aynı gün girdiği sınavlar arasında en az 3 saat boşluk olmalı.
 * 4) Sınıf kapasitesi öğrenci sayısını karşılamalı.
 * 5) Aynı sınıf aynı gün ve zaman diliminde iki sınava verilemez.
 * 
 * Yöntem:
 * - Çok kalabalık dersleri, en büyük sınıf kapasitesine sığacak şekilde dengeli parçalara böl.
 * - Sınavları kalabalıktan küçüğe sırala.
 * - Her sınav için gün 1'den başlayıp sırayla gün/slot dene; kurallara uyuyorsa ilk uygun yere yerleştir.
 * - Eğer bir ders bölündüyse: ilk parçanın yerleştiği gün/slot'u kilitle; diğer parçalar da aynı gün/slot'ta yerleşmeli.
 * - Backtracking ile daha iyi çözümler bul.
 */
public class SchedulerService {

    private ConstraintChecker constraintChecker;
    private Random random;
    private boolean useRandomization = true;

    // Pre-computed data structures for fast lookups
    private Map<String, List<Student>> courseStudentsMap;
    private Map<String, Integer> enrollmentCounts;
    private Map<String, Set<String>> studentCoursesMap;

    public SchedulerService() {
        this.constraintChecker = new ConstraintChecker();
        this.random = new Random();
    }

    public void setUseRandomization(boolean useRandomization) {
        this.useRandomization = useRandomization;
    }

    public void setRandomSeed(long seed) {
        this.random = new Random(seed);
    }

    public ExamTimetable generateTimetable(List<Course> courses, List<Classroom> classrooms,
            List<Enrollment> enrollments, LocalDate startDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        return generateTimetable(courses, classrooms, enrollments, startDate, startDate.plusDays(6));
    }

    public ExamTimetable generateTimetable(List<Course> courses, List<Classroom> classrooms,
            List<Enrollment> enrollments, LocalDate startDate, LocalDate endDate) {

        validateInputs(courses, classrooms, enrollments, startDate, endDate);

        if (endDate == null) {
            endDate = startDate.plusDays(6);
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        int maxDays = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
        System.out.println("\n=== SCHEDULER SERVICE: Backtracking with Course Splitting ===");
        System.out.println("Date range: " + startDate + " to " + endDate + " (" + maxDays + " days)");
        System.out.println("Courses to schedule: " + courses.size());
        System.out.println("Available classrooms: " + classrooms.size());
        System.out.println("Total enrollments: " + enrollments.size());
        System.out.println("Randomization: " + (useRandomization ? "ON" : "OFF"));

        // Bir öğrencinin aynı gün girdiği sınavlar arasında en az 3 saat boşluk olmalı
        constraintChecker.setMinGapMinutes(180);
        constraintChecker.setMaxExamsPerDay(2);

        buildLookupMaps(enrollments);

        List<Classroom> sortedClassrooms = classrooms.stream()
                .sorted(Comparator.comparingInt(Classroom::getCapacity).reversed())
                .collect(Collectors.toList());
        
        int maxClassroomCapacity = sortedClassrooms.isEmpty() ? 0 : sortedClassrooms.get(0).getCapacity();
        System.out.println("Max classroom capacity: " + maxClassroomCapacity);

        List<LocalTime> timeSlots = generateTimeSlots();
        System.out.println("Time slots available: " + timeSlots.size() + " slots");

        List<ExamPart> examParts = createExamParts(courses, maxClassroomCapacity);
        System.out.println("Total exam parts after splitting: " + examParts.size());

        // Save original randomization setting
        boolean originalRandomization = useRandomization;
        
        // Binary search must be deterministic to find true optimal
        useRandomization = false;
        
        int minDaysNeeded = calculateMinDaysNeeded(examParts, classrooms);
        int low = Math.max(1, minDaysNeeded);
        int high = maxDays;
        int optimalDays = -1;

        System.out.println("\nStarting binary search for optimal days (" + low + " - " + high + ")...");

        while (low <= high) {
            int mid = low + (high - low) / 2;
            System.out.println("\n>>> Trying " + mid + " day(s)...");

            ExamTimetable result = attemptScheduleBacktrack(mid, examParts, sortedClassrooms, timeSlots, enrollments, startDate);

            if (result != null) {
                System.out.println(">>> SUCCESS with " + mid + " day(s)! Trying fewer...");
                optimalDays = mid;
                high = mid - 1;
            } else {
                System.out.println(">>> FAILED with " + mid + " day(s)! Need more...");
                low = mid + 1;
            }
        }

        // Restore randomization for final schedule generation
        useRandomization = originalRandomization;

        if (optimalDays == -1) {
            throw new RuntimeException(
                    "Could not find a valid schedule within " + maxDays + " days. " +
                            "Constraints may be too tight. Try extending the date range.");
        }

        System.out.println("\n✓ OPTIMAL: " + optimalDays + " day(s)");
        System.out.println("Generating final schedule with randomization: " + useRandomization);

        // Generate final schedule with randomization for variety
        ExamTimetable bestResult = null;
        int maxAttempts = useRandomization ? 10 : 1;
        for (int attempt = 0; attempt < maxAttempts && bestResult == null; attempt++) {
            if (attempt > 0) {
                System.out.println("  Retry attempt " + (attempt + 1) + "...");
            }
            bestResult = attemptScheduleBacktrack(optimalDays, examParts, sortedClassrooms, timeSlots, enrollments, startDate);
        }
        
        // Fallback to deterministic if needed
        if (bestResult == null) {
            System.out.println("  Falling back to deterministic...");
            useRandomization = false;
            bestResult = attemptScheduleBacktrack(optimalDays, examParts, sortedClassrooms, timeSlots, enrollments, startDate);
            useRandomization = originalRandomization;
        }

        if (bestResult != null) {
            System.out.println("\n✓ OPTIMAL SCHEDULE FOUND: " + optimalDays + " day(s)");
            System.out.println("Total exams scheduled: " + bestResult.getExams().size());
            return bestResult;
        } else {
            throw new RuntimeException(
                    "Could not find a valid schedule within " + maxDays + " days. " +
                            "Constraints may be too tight. Try extending the date range.");
        }
    }

    public ScheduleOptions generateTimetableWithOptions(List<Course> courses, List<Classroom> classrooms,
            List<Enrollment> enrollments, LocalDate startDate, LocalDate endDate) {

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

        // Bir öğrencinin aynı gün girdiği sınavlar arasında en az 3 saat boşluk olmalı
        constraintChecker.setMinGapMinutes(180);
        constraintChecker.setMaxExamsPerDay(2);

        buildLookupMaps(enrollments);

        List<Classroom> sortedClassrooms = classrooms.stream()
                .sorted(Comparator.comparingInt(Classroom::getCapacity).reversed())
                .collect(Collectors.toList());
        
        int maxClassroomCapacity = sortedClassrooms.isEmpty() ? 0 : sortedClassrooms.get(0).getCapacity();
        List<LocalTime> timeSlots = generateTimeSlots();
        List<ExamPart> examParts = createExamParts(courses, maxClassroomCapacity);

        boolean originalRandomization = useRandomization;
        useRandomization = false;
        
        int minDaysNeeded = calculateMinDaysNeeded(examParts, classrooms);
        int low = Math.max(1, minDaysNeeded);
        int high = maxDays;
        int optimalDays = -1;

        System.out.println("Finding optimal schedule (deterministic)...");

        while (low <= high) {
            int mid = low + (high - low) / 2;
            ExamTimetable result = attemptScheduleBacktrack(mid, examParts, sortedClassrooms, timeSlots, enrollments, startDate);

            if (result != null) {
                optimalDays = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        useRandomization = originalRandomization;

        if (optimalDays == -1) {
            throw new RuntimeException(
                    "Could not find a valid schedule within " + maxDays + " days.");
        }

        System.out.println("\n✓ OPTIMAL: " + optimalDays + " day(s)");

        System.out.println("\nGenerating varied schedule for optimal days...");
        
        // Try multiple times with randomization to find a valid schedule
        ExamTimetable optimalSchedule = null;
        int maxAttempts = useRandomization ? 10 : 1;
        for (int attempt = 0; attempt < maxAttempts && optimalSchedule == null; attempt++) {
            if (attempt > 0) {
                System.out.println("  Retry attempt " + (attempt + 1) + "...");
            }
            optimalSchedule = attemptScheduleBacktrack(optimalDays, examParts, sortedClassrooms, 
                    timeSlots, enrollments, startDate);
        }
        
        // Fallback to deterministic if randomization failed
        if (optimalSchedule == null) {
            System.out.println("  Falling back to deterministic schedule...");
            useRandomization = false;
            optimalSchedule = attemptScheduleBacktrack(optimalDays, examParts, sortedClassrooms, 
                    timeSlots, enrollments, startDate);
            useRandomization = originalRandomization;
        }

        ScheduleOptions options = new ScheduleOptions(optimalDays, optimalSchedule);
        options.addOption(optimalDays, optimalSchedule);

        System.out.println("\nGenerating alternative schedules...");
        for (int extraDays = 1; extraDays <= 4; extraDays++) {
            int altDays = optimalDays + extraDays;
            if (altDays > maxDays) {
                break;
            }

            ExamTimetable altSchedule = attemptScheduleSpread(altDays, examParts, sortedClassrooms, 
                    timeSlots, enrollments, startDate);

            if (altSchedule != null) {
                options.addOption(altDays, altSchedule);
                System.out.println("  Generated " + altDays + "-day alternative");
            }
        }

        System.out.println("\n✓ Generated " + options.getAllOptions().size() + " schedule option(s)");
        return options;
    }

    private static class ExamPart {
        final Course course;
        final List<Student> students;
        final int partIndex;
        final int totalParts;
        final String groupId;

        ExamPart(Course course, List<Student> students, int partIndex, int totalParts) {
            this.course = course;
            this.students = students;
            this.partIndex = partIndex;
            this.totalParts = totalParts;
            this.groupId = course.getCode();
        }

        int getStudentCount() {
            return students.size();
        }

        boolean isFirstPart() {
            return partIndex == 0;
        }
    }

    private List<ExamPart> createExamParts(List<Course> courses, int maxClassroomCapacity) {
        List<ExamPart> allParts = new ArrayList<>();

        for (Course course : courses) {
            List<Student> students = courseStudentsMap.getOrDefault(course.getCode(), new ArrayList<>());
            int studentCount = students.size();

            if (studentCount == 0) {
                allParts.add(new ExamPart(course, new ArrayList<>(), 0, 1));
                continue;
            }

            if (maxClassroomCapacity <= 0 || studentCount <= maxClassroomCapacity) {
                allParts.add(new ExamPart(course, new ArrayList<>(students), 0, 1));
            } else {
                int numParts = (int) Math.ceil((double) studentCount / maxClassroomCapacity);
                int baseSize = studentCount / numParts;
                int remainder = studentCount % numParts;

                List<Student> shuffledStudents = new ArrayList<>(students);
                if (useRandomization) {
                    Collections.shuffle(shuffledStudents, random);
                }

                int startIdx = 0;
                for (int p = 0; p < numParts; p++) {
                    int partSize = baseSize + (p < remainder ? 1 : 0);
                    List<Student> partStudents = new ArrayList<>(shuffledStudents.subList(startIdx, startIdx + partSize));
                    allParts.add(new ExamPart(course, partStudents, p, numParts));
                    startIdx += partSize;
                }

                System.out.println("  Course " + course.getCode() + " split into " + numParts + 
                        " parts (" + studentCount + " students)");
            }
        }

        // Sort by student count descending, then by course code
        allParts.sort(Comparator
                .comparingInt(ExamPart::getStudentCount).reversed()
                .thenComparing(p -> p.course.getCode())
                .thenComparingInt(p -> p.partIndex));

        return allParts;
    }

    /**
     * Backtracking scheduling algorithm
     */
    private ExamTimetable attemptScheduleBacktrack(int maxDays, List<ExamPart> examParts,
            List<Classroom> classrooms, List<LocalTime> timeSlots,
            List<Enrollment> enrollments, LocalDate startDate) {

        ScheduleState state = new ScheduleState(courseStudentsMap);
        List<Exam> scheduledExams = new ArrayList<>();
        
        Map<String, Integer> classroomUsageCount = new HashMap<>();
        for (Classroom c : classrooms) {
            classroomUsageCount.put(c.getId(), 0);
        }

        // Group exam parts by course
        Map<String, List<ExamPart>> partsByCourse = examParts.stream()
                .collect(Collectors.groupingBy(p -> p.course.getCode()));

        // Get unique course codes in order (keep deterministic for correctness)
        List<String> courseCodes = examParts.stream()
                .map(p -> p.course.getCode())
                .distinct()
                .collect(Collectors.toList());

        long startTime = System.currentTimeMillis();
        long timeoutMs = 30000; // 30 second timeout

        boolean success = backtrack(0, courseCodes, partsByCourse, maxDays, classrooms, timeSlots,
                startDate, state, scheduledExams, classroomUsageCount, startTime, timeoutMs);

        if (success) {
            return new ExamTimetable(scheduledExams, enrollments);
        }
        return null;
    }

    /**
     * Recursive backtracking
     */
    private boolean backtrack(int courseIndex, List<String> courseCodes, 
            Map<String, List<ExamPart>> partsByCourse, int maxDays,
            List<Classroom> classrooms, List<LocalTime> timeSlots,
            LocalDate startDate, ScheduleState state, List<Exam> scheduledExams,
            Map<String, Integer> classroomUsageCount,
            long startTime, long timeoutMs) {

        // Check timeout
        if (System.currentTimeMillis() - startTime > timeoutMs) {
            return false;
        }

        // Base case: all courses scheduled
        if (courseIndex >= courseCodes.size()) {
            return true;
        }

        String courseCode = courseCodes.get(courseIndex);
        List<ExamPart> courseParts = partsByCourse.get(courseCode);
        Course course = courseParts.get(0).course;
        int examDuration = course.getExamDurationMinutes();

        // Prepare classrooms list
        List<Classroom> workingClassrooms = new ArrayList<>(classrooms);
        if (useRandomization) {
            Collections.shuffle(workingClassrooms, random);
        }

        // Prepare day order - shuffle for variety but still try all days
        List<Integer> dayOrder = new ArrayList<>();
        for (int i = 0; i < maxDays; i++) {
            dayOrder.add(i);
        }
        if (useRandomization) {
            Collections.shuffle(dayOrder, random);
        }

        // Try each day
        for (int dayOffset : dayOrder) {
            LocalDate date = startDate.plusDays(dayOffset);

            // Check if any student has max exams today
            boolean dayBlocked = false;
            for (ExamPart part : courseParts) {
                if (anyStudentHasMaxExamsOnDay(part.students, date, state)) {
                    dayBlocked = true;
                    break;
                }
            }
            if (dayBlocked) continue;

            // Prepare time slot order - shuffle for variety
            List<LocalTime> slotOrder = new ArrayList<>(timeSlots);
            if (useRandomization) {
                Collections.shuffle(slotOrder, random);
            }

            // Try each time slot
            for (LocalTime slotStart : slotOrder) {
                LocalTime slotEnd = slotStart.plusMinutes(examDuration);

                if (slotEnd.isAfter(LocalTime.of(18, 30))) {
                    continue;
                }

                ExamSlot slot = new ExamSlot(date, slotStart, slotEnd);

                // Try to place all parts at this slot
                List<Classroom> assignedClassrooms = new ArrayList<>();
                Set<String> newlyUsedClassrooms = new HashSet<>();
                boolean canPlaceAllParts = true;

                for (ExamPart part : courseParts) {
                    Classroom assigned = findSuitableClassroom(part, slot, workingClassrooms, 
                            newlyUsedClassrooms, classroomUsageCount, state);
                    
                    if (assigned == null) {
                        canPlaceAllParts = false;
                        break;
                    }
                    
                    assignedClassrooms.add(assigned);
                    newlyUsedClassrooms.add(assigned.getId());
                }

                if (!canPlaceAllParts) {
                    continue;
                }

                // Check student constraints: en az 3 saat (180 dk) boşluk olmalı
                boolean studentConstraintsOk = true;
                for (ExamPart part : courseParts) {
                    for (Student student : part.students) {
                        List<Exam> studentExamsToday = state.getExamsForStudentDate(student.getId(), date);
                        for (Exam existing : studentExamsToday) {
                            long gapMinutes = calculateGapMinutes(existing.getSlot(), slot);
                            if (gapMinutes < 180) {
                                studentConstraintsOk = false;
                                break;
                            }
                        }
                        if (!studentConstraintsOk) break;
                    }
                    if (!studentConstraintsOk) break;
                }

                if (!studentConstraintsOk) {
                    continue;
                }

                // Place all parts
                List<Exam> placedExams = new ArrayList<>();
                for (int i = 0; i < courseParts.size(); i++) {
                    ExamPart part = courseParts.get(i);
                    Classroom classroom = assignedClassrooms.get(i);
                    
                    Exam exam = new Exam(part.course, classroom, slot);
                    placedExams.add(exam);
                    scheduledExams.add(exam);
                    state.add(exam); // This now tracks classroom usage too
                    classroomUsageCount.merge(classroom.getId(), 1, Integer::sum);
                }

                // Recurse to next course
                if (backtrack(courseIndex + 1, courseCodes, partsByCourse, maxDays, classrooms, timeSlots,
                        startDate, state, scheduledExams, classroomUsageCount, startTime, timeoutMs)) {
                    return true;
                }

                // Backtrack: remove placed exams
                for (int i = 0; i < placedExams.size(); i++) {
                    Exam exam = placedExams.get(i);
                    scheduledExams.remove(scheduledExams.size() - 1);
                    state.removeLast(); // This now reverts classroom usage too
                    classroomUsageCount.merge(exam.getClassroom().getId(), -1, Integer::sum);
                }
            }
        }

        return false;
    }

    private Classroom findSuitableClassroom(ExamPart part, ExamSlot slot, List<Classroom> classrooms,
            Set<String> newlyUsedClassrooms, Map<String, Integer> classroomUsageCount,
            ScheduleState state) {
        
        int studentCount = part.getStudentCount();

        List<Classroom> sortedClassrooms = classrooms.stream()
                .filter(c -> c.getCapacity() >= studentCount)
                .filter(c -> !newlyUsedClassrooms.contains(c.getId()))
                .filter(c -> state.isClassroomAvailable(c.getId(), slot)) // Check time overlap
                .sorted(Comparator
                        .comparingInt(Classroom::getCapacity)
                        .thenComparingInt(c -> classroomUsageCount.getOrDefault(c.getId(), 0)))
                .collect(Collectors.toList());

        if (useRandomization && sortedClassrooms.size() > 1) {
            int minCapacity = sortedClassrooms.get(0).getCapacity();
            int threshold = minCapacity + 20;
            
            List<Classroom> candidates = sortedClassrooms.stream()
                    .filter(c -> c.getCapacity() <= threshold)
                    .collect(Collectors.toList());
            
            if (!candidates.isEmpty()) {
                return candidates.get(random.nextInt(candidates.size()));
            }
        }

        return sortedClassrooms.isEmpty() ? null : sortedClassrooms.get(0);
    }

    /**
     * Spread scheduling for alternative schedules
     */
    private ExamTimetable attemptScheduleSpread(int maxDays, List<ExamPart> examParts,
            List<Classroom> classrooms, List<LocalTime> timeSlots,
            List<Enrollment> enrollments, LocalDate startDate) {

        ScheduleState state = new ScheduleState(courseStudentsMap);
        List<Exam> scheduledExams = new ArrayList<>();
        
        Map<Integer, Integer> examsPerDay = new HashMap<>();
        for (int i = 0; i < maxDays; i++) {
            examsPerDay.put(i, 0);
        }
        Map<String, Integer> classroomUsageCount = new HashMap<>();
        for (Classroom c : classrooms) {
            classroomUsageCount.put(c.getId(), 0);
        }

        Map<String, List<ExamPart>> partsByCourse = examParts.stream()
                .collect(Collectors.groupingBy(p -> p.course.getCode()));

        Set<String> scheduledCourses = new HashSet<>();

        for (ExamPart examPart : examParts) {
            String courseCode = examPart.course.getCode();
            
            if (scheduledCourses.contains(courseCode)) {
                continue;
            }

            List<ExamPart> courseParts = partsByCourse.get(courseCode);
            
            boolean scheduled = scheduleCoursePartsSpread(courseParts, maxDays, classrooms, timeSlots,
                    startDate, state, scheduledExams, examsPerDay, classroomUsageCount);

            if (!scheduled) {
                return null;
            }

            scheduledCourses.add(courseCode);
        }

        return new ExamTimetable(scheduledExams, enrollments);
    }

    private boolean scheduleCoursePartsSpread(List<ExamPart> courseParts, int maxDays,
            List<Classroom> classrooms, List<LocalTime> timeSlots,
            LocalDate startDate, ScheduleState state, List<Exam> scheduledExams,
            Map<Integer, Integer> examsPerDay, Map<String, Integer> classroomUsageCount) {

        Course course = courseParts.get(0).course;
        int examDuration = course.getExamDurationMinutes();

        List<Classroom> workingClassrooms = new ArrayList<>(classrooms);
        if (useRandomization) {
            Collections.shuffle(workingClassrooms, random);
        }

        // Sort days by exam count (prefer emptier days)
        List<Integer> dayOffsets = new ArrayList<>();
        for (int i = 0; i < maxDays; i++) {
            dayOffsets.add(i);
        }
        dayOffsets.sort(Comparator.comparingInt(d -> examsPerDay.getOrDefault(d, 0)));

        for (int dayOffset : dayOffsets) {
            LocalDate date = startDate.plusDays(dayOffset);

            boolean dayBlocked = false;
            for (ExamPart part : courseParts) {
                if (anyStudentHasMaxExamsOnDay(part.students, date, state)) {
                    dayBlocked = true;
                    break;
                }
            }
            if (dayBlocked) continue;

            // Prepare time slot order - shuffle for variety
            List<LocalTime> slotOrder = new ArrayList<>(timeSlots);
            if (useRandomization) {
                Collections.shuffle(slotOrder, random);
            }

            for (LocalTime slotStart : slotOrder) {
                LocalTime slotEnd = slotStart.plusMinutes(examDuration);

                if (slotEnd.isAfter(LocalTime.of(18, 30))) {
                    continue;
                }

                ExamSlot slot = new ExamSlot(date, slotStart, slotEnd);

                List<Classroom> assignedClassrooms = new ArrayList<>();
                Set<String> newlyUsedClassrooms = new HashSet<>();
                boolean canPlaceAllParts = true;

                for (ExamPart part : courseParts) {
                    Classroom assigned = findSuitableClassroom(part, slot, workingClassrooms, 
                            newlyUsedClassrooms, classroomUsageCount, state);
                    
                    if (assigned == null) {
                        canPlaceAllParts = false;
                        break;
                    }
                    
                    assignedClassrooms.add(assigned);
                    newlyUsedClassrooms.add(assigned.getId());
                }

                if (!canPlaceAllParts) {
                    continue;
                }

                // Check student constraints: en az 3 saat (180 dk) boşluk olmalı
                boolean studentConstraintsOk = true;
                for (ExamPart part : courseParts) {
                    for (Student student : part.students) {
                        List<Exam> studentExamsToday = state.getExamsForStudentDate(student.getId(), date);
                        for (Exam existing : studentExamsToday) {
                            long gapMinutes = calculateGapMinutes(existing.getSlot(), slot);
                            if (gapMinutes < 180) {
                                studentConstraintsOk = false;
                                break;
                            }
                        }
                        if (!studentConstraintsOk) break;
                    }
                    if (!studentConstraintsOk) break;
                }

                if (!studentConstraintsOk) {
                    continue;
                }

                // Place all parts
                for (int i = 0; i < courseParts.size(); i++) {
                    ExamPart part = courseParts.get(i);
                    Classroom classroom = assignedClassrooms.get(i);
                    
                    Exam exam = new Exam(part.course, classroom, slot);
                    scheduledExams.add(exam);
                    state.add(exam);
                    classroomUsageCount.merge(classroom.getId(), 1, Integer::sum);
                }
                
                examsPerDay.merge(dayOffset, 1, Integer::sum);
                return true;
            }
        }

        return false;
    }

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
            System.out.println("Warning: Start date is in the past");
        }
    }

    private void buildLookupMaps(List<Enrollment> enrollments) {
        System.out.println("\nBuilding lookup maps...");

        courseStudentsMap = enrollments.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCourse().getCode(),
                        Collectors.mapping(Enrollment::getStudent, Collectors.toList())));

        enrollmentCounts = new HashMap<>();
        for (Map.Entry<String, List<Student>> entry : courseStudentsMap.entrySet()) {
            enrollmentCounts.put(entry.getKey(), entry.getValue().size());
        }

        studentCoursesMap = new HashMap<>();
        for (Enrollment e : enrollments) {
            String studentId = e.getStudent().getId();
            String courseCode = e.getCourse().getCode();
            studentCoursesMap.computeIfAbsent(studentId, k -> new HashSet<>()).add(courseCode);
        }

        System.out.println("  Courses with enrollments: " + courseStudentsMap.size());
        System.out.println("  Unique students: " + studentCoursesMap.size());
    }

    private List<LocalTime> generateTimeSlots() {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime start = LocalTime.of(9, 0);

        while (!start.isAfter(LocalTime.of(18, 0))) {
            slots.add(start);
            start = start.plusMinutes(30);
        }
        return slots;
    }

    private int calculateMinDaysNeeded(List<ExamPart> examParts, List<Classroom> classrooms) {
        Set<String> uniqueCourses = examParts.stream()
                .map(p -> p.course.getCode())
                .collect(Collectors.toSet());
        int courseCount = uniqueCourses.size();

        double totalExamMinutes = examParts.stream()
                .filter(ExamPart::isFirstPart)
                .mapToDouble(p -> p.course.getExamDurationMinutes())
                .sum();
        double dailyClassroomMinutes = classrooms.size() * 570.0;
        int minDaysForCapacity = (int) Math.ceil(totalExamMinutes / dailyClassroomMinutes);

        long maxExamsForStudent = studentCoursesMap.values().stream()
                .mapToLong(Set::size)
                .max()
                .orElse(0);
        int minDaysForStudents = (int) Math.ceil((double) maxExamsForStudent / 2.0);

        int slotsPerDay = 3;
        int minDaysForSlots = (int) Math.ceil((double) courseCount / (classrooms.size() * slotsPerDay));

        int result = Math.max(Math.max(minDaysForCapacity, minDaysForStudents), minDaysForSlots);
        System.out.println("  Minimum days estimate: " + result + " (capacity=" + minDaysForCapacity + 
                ", students=" + minDaysForStudents + ", slots=" + minDaysForSlots + ")");
        return result;
    }

    private long calculateGapMinutes(ExamSlot slot1, ExamSlot slot2) {
        if (!slot1.getDate().equals(slot2.getDate())) {
            return Long.MAX_VALUE;
        }

        LocalTime end1 = slot1.getEndTime();
        LocalTime start1 = slot1.getStartTime();
        LocalTime end2 = slot2.getEndTime();
        LocalTime start2 = slot2.getStartTime();

        if (end1.isBefore(start2) || end1.equals(start2)) {
            return java.time.Duration.between(end1, start2).toMinutes();
        } else if (end2.isBefore(start1) || end2.equals(start1)) {
            return java.time.Duration.between(end2, start1).toMinutes();
        } else {
            return 0;
        }
    }

    private boolean anyStudentHasMaxExamsOnDay(List<Student> students, LocalDate date, ScheduleState state) {
        for (Student s : students) {
            if (state.getExamsCountForStudentDate(s.getId(), date) >= 2) {
                return true;
            }
        }
        return false;
    }
}
