package com.examplanner.services;

import com.examplanner.domain.Exam;
import com.examplanner.domain.Student;
import com.examplanner.domain.Enrollment;

import java.time.LocalDate;
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

    // Enrollment lookup (COURSE_CODE -> List<Student>)
    private final Map<String, List<Student>> courseStudentsMap;

    public ScheduleState(Map<String, List<Student>> courseStudentsMap) {
        this.examsList = new ArrayList<>();
        this.studentDailyCounts = new HashMap<>(); // Lazy init inner maps
        this.studentDailyExams = new HashMap<>();
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
                if (list != null) {
                    list.remove(list.size() - 1); // Remove object reference, but since we add sequentially, removing
                                                  // last is fine?
                    // Wait, removing by object reference is safer if we ensure "exam" is the exact
                    // instance.
                    // Actually list.remove(Object) is O(N) for that small list.
                    // Optimization: We know we just added it. Is it always the last one added?
                    // Not necessarily for a student if we scheduled another exam for them in
                    // between?
                    // No, "add" adds to the global schedule.
                    // "removeLast" removes the *very last* "add" call.
                    // So for this student, this exam MIGHT be the last one added to their list?
                    // Yes, because we traverse exams in order of Schedule generation.
                    // So we can remove the last element of the list, IF we guarantee insertion
                    // order logic.
                    // Ideally: list.remove(exam);
                }
            }
        }
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
