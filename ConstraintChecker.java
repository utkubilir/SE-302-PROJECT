import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class ConstraintChecker {
    private static final LocalTime MIN_START_TIME = LocalTime.of(9, 0);
    private static final LocalTime MAX_END_TIME = LocalTime.of(18, 30);
    private long minGapMinutes = 30;
    private int maxExamsPerDay = 2;

    public void setMinGapMinutes(long minGapMinutes) { this.minGapMinutes = minGapMinutes; }
    public void setMaxExamsPerDay(int maxExamsPerDay) { this.maxExamsPerDay = maxExamsPerDay; }

    public boolean checkAll(Exam candidateExam, ScheduleState state) {
        if (!isWithinTimeWindow(candidateExam.getSlot())) return false;

        List<Student> students = state.getStudentsForCourse(candidateExam.getCourse().getCode());
        if (students.size() > candidateExam.getClassroom().getCapacity()) return false;
        if (!isClassroomAvailable(candidateExam.getClassroom(), candidateExam.getSlot(), state.getExams())) return false;

        return true;
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
            if (exam.getClassroom().getId().equals(classroom.getId()) && exam.getSlot().overlaps(slot)) return false;
        }
        return true;
    }
}