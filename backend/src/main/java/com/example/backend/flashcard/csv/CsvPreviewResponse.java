package com.example.backend.flashcard.csv;

import java.util.List;
import java.util.Map;
import com.example.backend.flashcard.dto.request.SaveFlashcardRequest;

public record CsvPreviewResponse(List<Row> rows, List<FileError> errors, int totalRows,
                                 int validRows, int invalidRows, boolean valid) {
    public record Row(long rowNumber, SaveFlashcardRequest data, Map<String, String> fieldErrors) { }
    public record FileError(long rowNumber, String message) { }

    public static CsvPreviewResponse of(List<Row> rows, List<FileError> errors) {
        int good = (int) rows.stream().filter(row -> row.data() != null && row.fieldErrors().isEmpty()).count();
        return new CsvPreviewResponse(List.copyOf(rows), List.copyOf(errors), rows.size(), good, rows.size() - good,
                errors.isEmpty() && !rows.isEmpty() && good == rows.size());
    }
}
