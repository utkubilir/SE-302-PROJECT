import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        constraintChecker.setMinGapMinutes(minGap);
        constraintChecker.setMaxExamsPerDay(2);

        List<Course> sortedCourses = new ArrayList<>(courses);
        sortedCourses.sort(Comparator.comparingInt((Course c) -> getStudentCount(c, enrollments)).reversed());

        List<Classroom> sortedClassrooms = new ArrayList<>(classrooms);
        sortedClassrooms.sort(Comparator.comparingInt(Classroom::getCapacity));

        int days = calculateMinDaysNeeded(courses, classrooms, enrollments);

        Map<String, List<Student>> courseStudentsMap = enrollments.stream()
                .collect(Collectors.groupingBy(e -> e.getCourse().getCode(),
                        Collectors.mapping(Enrollment::getStudent, Collectors.toList())));

        int low = days;
        int high = 50;
        ExamTimetable bestResult = null;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            ExamTimetable result = null;

            if (result != null) {
                bestResult = result;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return bestResult;
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

        return Math.max(1, Math.max(minDaysForCapacity, minDaysForStudents) + 1);
    }
}