package com.examplanner.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.function.BiPredicate;

public class SearchableDialog<T> extends Dialog<T> {

    private final FilteredList<T> filteredList;

    public SearchableDialog(String title, String headerText, List<T> items, BiPredicate<T, String> filterLogic) {
        this(title, headerText, items, filterLogic, false);
    }

    public SearchableDialog(String title, String headerText, List<T> items, BiPredicate<T, String> filterLogic,
            boolean isDarkMode) {
        setTitle(title);
        setHeaderText(headerText);

        // UI Components
        TextField searchField = new TextField();
        searchField.setPromptText("Type to search...");

        ObservableList<T> observableItems = FXCollections.observableArrayList(items);
        filteredList = new FilteredList<>(observableItems, p -> true);

        ListView<T> listView = new ListView<>(filteredList);
        listView.setPrefHeight(200);

        // Filter Logic
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            filteredList.setPredicate(item -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                return filterLogic.test(item, newValue.toLowerCase());
            });
        });

        // Layout
        VBox content = new VBox(10);
        content.getChildren().addAll(searchField, listView);
        getDialogPane().setContent(content);

        // Buttons
        ButtonType okButtonType = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        // Apply dark mode
        getDialogPane().getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        if (isDarkMode) {
            getDialogPane().getStyleClass().add("dark-mode");
        }

        // Result Converter
        setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });
    }
}
