package com.detextion.notes;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.sql.SQLException;
import java.util.List;

public class NoteEditorController {

    @FXML private TextField titleField;
    @FXML private TextArea contentArea;
    @FXML private ListView<String> noteListView;

    private final NoteService noteService = new NoteService();
    private final ObservableList<String> notesTitles = FXCollections.observableArrayList();
    private List<Note> noteObjects;

    @FXML
    public void initialize() {
        noteListView.setItems(notesTitles);
        noteListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            int idx = newVal.intValue();
            if (idx >= 0 && idx < noteObjects.size()) {
                Note note = noteObjects.get(idx);
                titleField.setText(note.getTitle());
                contentArea.setText(note.getContent());
            }
        });
        loadNotes();
    }

    /** 💾 Save note to DB */
    @FXML
    private void onSaveClicked() {
        String title = titleField.getText().trim();
        String content = contentArea.getText().trim();

        if (title.isEmpty()) {
            showAlert("Title required!", "Please enter a title for your note.");
            return;
        }

        try {
            noteService.saveNote(title, content);
            showAlert("Success", "✅ Note saved successfully!");
            loadNotes();
            clearFields();
        } catch (SQLException e) {
            showAlert("Database Error", "Couldn't save note: " + e.getMessage());
        }
    }

    /** 🔁 Reload notes from DB */
    @FXML
    private void onLoadNotesClicked() {
        loadNotes();
    }

    /** 🗑 Clear title and content fields */
    @FXML
    private void onClearClicked() {
        clearFields();
    }

    private void clearFields() {
        titleField.clear();
        contentArea.clear();
        noteListView.getSelectionModel().clearSelection();
    }

    private void loadNotes() {
        try {
            noteObjects = noteService.getAllNotes();
            notesTitles.clear();
            for (Note n : noteObjects) {
                notesTitles.add(n.getTitle());
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Couldn't load notes: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
