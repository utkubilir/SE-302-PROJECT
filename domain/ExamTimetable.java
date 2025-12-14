import java.util.ArrayList;
import java.util.List;

public class ExamTimetable {
    private List<Exam> exams;
    private List<Enrollment> enrollments;

    public ExamTimetable() {
        this.exams = new ArrayList<>();
        this.enrollments = new ArrayList<>();
    }

    public ExamTimetable(List<Exam> exams, List<Enrollment> enrollments) {
        this.exams = exams;
        this.enrollments = enrollments;
    }

    
    public ExamTimetable(List<Exam> exams) {
        this(exams, new ArrayList<>());
    }

    public List<Exam> getExams() {
        return exams;
    }

    public void addExam(Exam exam) {
        this.exams.add(exam);
    }

    public void setExams(List<Exam> exams) {
        this.exams = exams;
    }

    
    public List<Exam> getExamsForCourse(com.examplanner.domain.Course course) {
        List<Exam> result = new ArrayList<>();
        for (Exam exam : exams) {
            if (exam.getCourse().getCode().equals(course.getCode())) {
                result.add(exam);
            }
        }
        return result;
    }

    
    public List<Exam> getExamsForStudent(com.examplanner.domain.Student student) {
        List<Exam> result = new ArrayList<>();
        // Pre-fetch student's enrolled course codes for efficiency
        java.util.Set<String> enrolledCourseCodes = new java.util.HashSet<>();
        for (Enrollment e : enrollments) {
            if (e.getStudent().getId().equals(student.getId())) {
                enrolledCourseCodes.add(e.getCourse().getCode());
            }
        }

        for (Exam exam : exams) {
            if (enrolledCourseCodes.contains(exam.getCourse().getCode())) {
                result.add(exam);
            }
        }
        return result;
    }
}