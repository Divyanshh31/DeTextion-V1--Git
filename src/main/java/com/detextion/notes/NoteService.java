package com.detextion.notes;

import java.sql.*;
import java.util.*;

public class NoteService {
    private static final String DB_URL = "jdbc:sqlite:notes.db"; // creates a file notes.db in project workspace

    static {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, content TEXT)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveNote(String title, String content) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT INTO notes (title, content) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, title);
                pstmt.setString(2, content);
                pstmt.executeUpdate();
            }
        }
    }

    public List<Note> getAllNotes() throws SQLException {
        List<Note> notes = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM notes")) {
            while (rs.next()) {
                notes.add(new Note(rs.getInt("id"), rs.getString("title"), rs.getString("content")));
            }
        }
        return notes;
    }

    // add methods for update, delete as needed
}