package com.examplanner.ui;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Helper class for creating and managing icons throughout the application.
 * Uses Ikonli with FontAwesome5 icon pack.
 */
public class IconHelper {

    // ============== Data Import Card Icons ==============

    public static FontIcon courses() {
        return createIcon("fas-file-alt", 24, "icon-primary");
    }

    public static FontIcon students() {
        return createIcon("fas-graduation-cap", 24, "icon-primary");
    }

    public static FontIcon classrooms() {
        return createIcon("fas-building", 24, "icon-primary");
    }

    public static FontIcon attendance() {
        return createIcon("fas-users", 24, "icon-primary");
    }

    // ============== Navigation Icons ==============

    public static FontIcon settings() {
        return createIcon("fas-cog", 16);
    }

    public static FontIcon manual() {
        return createIcon("fas-book-open", 16);
    }

    public static FontIcon dashboard() {
        return createIcon("fas-chart-bar", 16);
    }

    public static FontIcon timetable() {
        return createIcon("fas-calendar-alt", 16);
    }

    public static FontIcon dataImport() {
        return createIcon("fas-file-import", 16);
    }

    public static FontIcon studentSearch() {
        return createIcon("fas-user-graduate", 16);
    }

    public static FontIcon studentPortal() {
        return createIcon("fas-door-open", 16);
    }

    public static FontIcon exit() {
        return createIcon("fas-sign-out-alt", 16);
    }

    // ============== Action Icons ==============

    public static FontIcon search() {
        return createIcon("fas-search", 14);
    }

    public static FontIcon filter() {
        return createIcon("fas-filter", 14);
    }

    public static FontIcon history() {
        return createIcon("fas-history", 14);
    }

    public static FontIcon validate() {
        return createIcon("fas-check-circle", 14);
    }

    public static FontIcon conflicts() {
        return createIcon("fas-exclamation-triangle", 14);
    }

    public static FontIcon export() {
        return createIcon("fas-file-export", 14);
    }

    public static FontIcon exportPdf() {
        return createIcon("fas-file-pdf", 14);
    }

    public static FontIcon generate() {
        return createIcon("fas-magic", 14);
    }

    public static FontIcon back() {
        return createIcon("fas-arrow-left", 14);
    }

    public static FontIcon delete() {
        return createIcon("fas-trash-alt", 14);
    }

    // ============== Status Icons ==============

    public static FontIcon success() {
        return createIcon("fas-check-circle", 16, "icon-success");
    }

    public static FontIcon warning() {
        return createIcon("fas-exclamation-triangle", 16, "icon-warning");
    }

    public static FontIcon error() {
        return createIcon("fas-times-circle", 16, "icon-error");
    }

    public static FontIcon info() {
        return createIcon("fas-info-circle", 16, "icon-primary");
    }

    // ============== Content Icons ==============

    public static FontIcon tip() {
        FontIcon icon = createIcon("fas-lightbulb", 18);
        icon.setStyle("-fx-icon-color: #F59E0B;");
        return icon;
    }

    public static FontIcon user() {
        return createIcon("fas-user", 16);
    }

    public static FontIcon conflict() {
        return createIcon("fas-exclamation-circle", 14, "icon-error");
    }

    public static FontIcon school() {
        return createIcon("fas-school", 16);
    }

    // ============== Factory Methods ==============

    private static FontIcon createIcon(String iconCode, int size) {
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(size);
        icon.getStyleClass().add("icon");
        return icon;
    }

    private static FontIcon createIcon(String iconCode, int size, String styleClass) {
        FontIcon icon = createIcon(iconCode, size);
        icon.getStyleClass().add(styleClass);
        return icon;
    }

    /**
     * Create a custom icon with specified parameters
     */
    public static FontIcon custom(String iconCode, int size, String color) {
        FontIcon icon = createIcon(iconCode, size);
        icon.setStyle("-fx-icon-color: " + color + ";");
        return icon;
    }
}
