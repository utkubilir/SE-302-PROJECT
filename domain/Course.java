public class Course {
    private String code;
    private String name;
    private int examDurationMinutes;

    public Course(String code, String name, int examDurationMinutes) {
        this.code = code;
        this.name = name;
        this.examDurationMinutes = examDurationMinutes;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getExamDurationMinutes() {
        return examDurationMinutes;
    }

    @Override
    public String toString() {
        return code + " - " + name;
    }
}
