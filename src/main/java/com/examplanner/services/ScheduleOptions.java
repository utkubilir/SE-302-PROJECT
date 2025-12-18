package com.examplanner.services;

import com.examplanner.domain.ExamTimetable;
import java.util.List;
import java.util.ArrayList;

/**
 * Holds multiple schedule options for user selection.
 * Contains the optimal schedule (minimum days) and alternative schedules
 * with more days for flexibility.
 */
public class ScheduleOptions {

    private final int optimalDays;
    private final ExamTimetable optimalSchedule;
    private final List<ScheduleOption> allOptions;

    public ScheduleOptions(int optimalDays, ExamTimetable optimalSchedule) {
        this.optimalDays = optimalDays;
        this.optimalSchedule = optimalSchedule;
        this.allOptions = new ArrayList<>();
    }

    public void addOption(int days, ExamTimetable schedule) {
        allOptions.add(new ScheduleOption(days, schedule, days == optimalDays));
    }

    public int getOptimalDays() {
        return optimalDays;
    }

    public ExamTimetable getOptimalSchedule() {
        return optimalSchedule;
    }

    public List<ScheduleOption> getAllOptions() {
        return allOptions;
    }

    /**
     * Represents a single schedule option.
     */
    public static class ScheduleOption {
        private final int days;
        private final ExamTimetable schedule;
        private final boolean isOptimal;

        public ScheduleOption(int days, ExamTimetable schedule, boolean isOptimal) {
            this.days = days;
            this.schedule = schedule;
            this.isOptimal = isOptimal;
        }

        public int getDays() {
            return days;
        }

        public ExamTimetable getSchedule() {
            return schedule;
        }

        public boolean isOptimal() {
            return isOptimal;
        }

        @Override
        public String toString() {
            if (isOptimal) {
                return days + " gün (Optimal ✓)";
            }
            return days + " gün";
        }
    }
}
