package com.examplanner.services;

import com.examplanner.domain.Exam;
import com.examplanner.domain.ExamSlot;
import com.examplanner.domain.Student;
import com.examplanner.domain.Enrollment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * optimized state holder for the backtracking scheduler.
 * Maintains indices for fast constraint checking.
 */
public class ScheduleState {

    private final List<Exam> examsList;

    // Index: StudentID -> Date -> Count of Exams
    private final Map<String, Map<LocalDate, Integer>> studentDailyCounts;

    // Index: StudentID -> Date -> Listen of Exams (for gap checks)
    private final Map<String, Map<LocalDate, List<Exam>>> studentDailyExams;

    // Index: ClassroomID -> Date -> List of Exams (for classroom conflict checks)
    private final Map<String, Map<LocalDate, List<Exam>>> classroomDailyExams;

    // Enrollment lookup (COURSE_CODE -> List<Student>)
    private final Map<String, List<Student>> courseStudentsMap;

    public ScheduleState(Map<String, List<Student>> courseStudentsMap) {
        this.examsList = new ArrayList<>();
        this.studentDailyCounts = new HashMap<>(); // Lazy init inner maps
        this.studentDailyExams = new HashMap<>();
        this.classroomDailyExams = new HashMap<>();
        this.courseStudentsMap = courseStudentsMap;
    }

    public void add(Exam exam) {
        examsList.add(exam);

        // Update indices
        String courseCode = exam.getCourse().getCode();
        LocalDate date = exam.getSlot().getDate();
        List<Student> students = courseStudentsMap.getOrDefault(courseCode, Collections.emptyList());

        for (Student s : students) {
            String sid = s.getId();

            // 1. Update Counts
            studentDailyCounts.putIfAbsent(sid, new HashMap<>());
            Map<LocalDate, Integer> dailyCounts = studentDailyCounts.get(sid);
            dailyCounts.put(date, dailyCounts.getOrDefault(date, 0) + 1);

            // 2. Update Exam List (for gaps)
            studentDailyExams.putIfAbsent(sid, new HashMap<>());
            Map<LocalDate, List<Exam>> dailyExams = studentDailyExams.get(sid);
            if (!dailyExams.containsKey(date)) {
                dailyExams.put(date, new ArrayList<>());
            }
            dailyExams.get(date).add(exam);
        }

        // 3. Update Classroom usage
        String classroomId = exam.getClassroom().getId();
        classroomDailyExams.putIfAbsent(classroomId, new HashMap<>());
        Map<LocalDate, List<Exam>> classroomExams = classroomDailyExams.get(classroomId);
        if (!classroomExams.containsKey(date)) {
            classroomExams.put(date, new ArrayList<>());
        }
        classroomExams.get(date).add(exam);
    }

    public void removeLast() {
        if (examsList.isEmpty())
            return;

        Exam exam = examsList.remove(examsList.size() - 1);

        String courseCode = exam.getCourse().getCode();
        LocalDate date = exam.getSlot().getDate();
        List<Student> students = courseStudentsMap.getOrDefault(courseCode, Collections.emptyList());

        for (Student s : students) {
            String sid = s.getId();

            // 1. Revert Counts
            Map<LocalDate, Integer> dailyCounts = studentDailyCounts.get(sid);
            if (dailyCounts != null) {
                int count = dailyCounts.getOrDefault(date, 0);
                if (count <= 1) {
                    dailyCounts.remove(date);
                } else {
                    dailyCounts.put(date, count - 1);
                }
            }

            // 2. Revert Exam List
            Map<LocalDate, List<Exam>> dailyExams = studentDailyExams.get(sid);
            if (dailyExams != null) {
                List<Exam> list = dailyExams.get(date);
                if (list != null && !list.isEmpty()) {
                    list.remove(list.size() - 1);
                }
            }
        }

        // 3. Revert Classroom usage
        String classroomId = exam.getClassroom().getId();
        Map<LocalDate, List<Exam>> classroomExams = classroomDailyExams.get(classroomId);
        if (classroomExams != null) {
            List<Exam> list = classroomExams.get(date);
            if (list != null && !list.isEmpty()) {
                list.remove(list.size() - 1);
            }
        }
    }

    /**
     * Check if a classroom is available for the given slot (no time overlap)
     */
    public boolean isClassroomAvailable(String classroomId, ExamSlot slot) {
        Map<LocalDate, List<Exam>> dailyExams = classroomDailyExams.get(classroomId);
        if (dailyExams == null) return true;
        
        List<Exam> examsOnDate = dailyExams.get(slot.getDate());
        if (examsOnDate == null || examsOnDate.isEmpty()) return true;
        
        LocalTime newStart = slot.getStartTime();
        LocalTime newEnd = slot.getEndTime();
        
        for (Exam existing : examsOnDate) {
            LocalTime existingStart = existing.getSlot().getStartTime();
            LocalTime existingEnd = existing.getSlot().getEndTime();
            
            // Check for time overlap
            // Two intervals [a,b] and [c,d] overlap if a < d && c < b
            if (newStart.isBefore(existingEnd) && existingStart.isBefore(newEnd)) {
                return false; // Overlap detected
            }
        }
        return true;
    }

    public List<Exam> getExams() {
        return examsList;
    }

    public int getExamsCountForStudentDate(String studentId, LocalDate date) {
        if (!studentDailyCounts.containsKey(studentId))
            return 0;
        return studentDailyCounts.get(studentId).getOrDefault(date, 0);
    }

    public List<Exam> getExamsForStudentDate(String studentId, LocalDate date) {
        if (!studentDailyExams.containsKey(studentId))
            return Collections.emptyList();
        return studentDailyExams.get(studentId).getOrDefault(date, Collections.emptyList());
    }

    public List<Student> getStudentsForCourse(String courseCode) {
        return courseStudentsMap.getOrDefault(courseCode, Collections.emptyList());
    }
}
