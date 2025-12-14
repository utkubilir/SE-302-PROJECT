import java.time.LocalDate;
import java.util.*;

public class ScheduleState {

    private final List<Exam> examsList;
    private final Map<String, Map<LocalDate, Integer>> studentDailyCounts;
    private final Map<String, Map<LocalDate, List<Exam>>> studentDailyExams;
    private final Map<String, List<Student>> courseStudentsMap;

    public ScheduleState(Map<String, List<Student>> courseStudentsMap) {
        this.examsList = new ArrayList<>();
        this.studentDailyCounts = new HashMap<>();
        this.studentDailyExams = new HashMap<>();
        this.courseStudentsMap = courseStudentsMap;
    }
    public void add(Exam exam) {
        examsList.add(exam);
        String courseCode = exam.getCourse().getCode();
        LocalDate date = exam.getSlot().getDate();
        List<Student> students = courseStudentsMap.getOrDefault(courseCode, Collections.emptyList());

        for (Student s : students) {
            String sid = s.getId();
            studentDailyCounts.putIfAbsent(sid, new HashMap<>());
            Map<LocalDate, Integer> dailyCounts = studentDailyCounts.get(sid);
            dailyCounts.put(date, dailyCounts.getOrDefault(date, 0) + 1);

            studentDailyExams.putIfAbsent(sid, new HashMap<>());
            Map<LocalDate, List<Exam>> dailyExams = studentDailyExams.get(sid);
            if (!dailyExams.containsKey(date)) {
                dailyExams.put(date, new ArrayList<>());
            }
            dailyExams.get(date).add(exam);
        }
    }

    public void removeLast() {
        if (examsList.isEmpty()) return;
        Exam exam = examsList.remove(examsList.size() - 1);
        String courseCode = exam.getCourse().getCode();
        LocalDate date = exam.getSlot().getDate();
        List<Student> students = courseStudentsMap.getOrDefault(courseCode, Collections.emptyList());

        for (Student s : students) {
            String sid = s.getId();
            Map<LocalDate, Integer> dailyCounts = studentDailyCounts.get(sid);
            if (dailyCounts != null) {
                int count = dailyCounts.getOrDefault(date, 0);
                if (count <= 1) dailyCounts.remove(date); else dailyCounts.put(date, count - 1);
            }
            Map<LocalDate, List<Exam>> dailyExams = studentDailyExams.get(sid);
            if (dailyExams != null) {
                List<Exam> list = dailyExams.get(date);
                if (list != null) list.remove(list.size() - 1);
            }
        }
    }
    
    public List<Exam> getExams() { return examsList; }

    public int getExamsCountForStudentDate(String studentId, LocalDate date) {
        if (!studentDailyCounts.containsKey(studentId)) return 0;
        return studentDailyCounts.get(studentId).getOrDefault(date, 0);
    }

    public List<Exam> getExamsForStudentDate(String studentId, LocalDate date) {
        if (!studentDailyExams.containsKey(studentId)) return Collections.emptyList();
        return studentDailyExams.get(studentId).getOrDefault(date, Collections.emptyList());
    }

    public List<Student> getStudentsForCourse(String courseCode) {
        return courseStudentsMap.getOrDefault(courseCode, Collections.emptyList());
    }
}