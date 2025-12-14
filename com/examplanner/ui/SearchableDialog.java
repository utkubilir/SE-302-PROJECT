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
        setTitle(title);
        setHeaderText(headerText);

        TextField searchField = new TextField();
        searchField.setPromptText("Type to search...");

        ObservableList<T> observableItems = FXCollections.observableArrayList(items);
        filteredList = new FilteredList<>(observableItems, p -> true);

        ListView<T> listView = new ListView<>(filteredList);
        listView.setPrefHeight(200);

        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            filteredList.setPredicate(item -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                return filterLogic.test(item, newValue.toLowerCase());
            });
        });