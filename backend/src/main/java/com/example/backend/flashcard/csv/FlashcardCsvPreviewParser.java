package com.example.backend.flashcard.csv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.backend.flashcard.dto.request.SaveFlashcardRequest;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

@Component
public class FlashcardCsvPreviewParser {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_ROWS = 1000;
    private static final Set<String> COLUMNS = Set.of("question", "answer", "example_sentence", "translated_example", "image_url");
    private static final Map<String, String> FIELDS = Map.of("question", "question", "answer", "answer",
            "exampleSentence", "example_sentence", "translatedExample", "translated_example", "imageUrl", "image_url");
    private final Validator validator;
    public FlashcardCsvPreviewParser(Validator validator) { this.validator = validator; }

    public CsvPreviewResponse parse(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new CsvPreviewException(HttpStatus.CONTENT_TOO_LARGE, "CSV file must not exceed 1 MiB.");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) { return failure(1, "CSV must use valid UTF-8 encoding."); }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        if (text.indexOf('\0') >= 0) return failure(new SourceLines(text).at(text.indexOf('\0')), "CSV must not contain null characters.");
        var rows = new ArrayList<CsvPreviewResponse.Row>();
        var errors = new ArrayList<CsvPreviewResponse.FileError>();
        // Do not let the parser skip blank lines: record offsets must map to physical source lines.
        try (var parser = CSVParser.parse(text, CSVFormat.RFC4180.builder().setIgnoreEmptyLines(false).get())) {
            var records = parser.iterator();
            var position = new SourceLines(text);
            long currentRow = 1;
            try {
                if (!records.hasNext()) return failure(1, "CSV must contain a header row with question and answer columns.");
                CSVRecord header = records.next();
                var columns = new LinkedHashMap<String, Integer>();
                for (int i = 0; i < header.size(); i++) {
                    String column = header.get(i).trim();
                    if (!COLUMNS.contains(column) || columns.putIfAbsent(column, i) != null)
                        return failure(1, "CSV headers must be unique and use only the documented column names.");
                }
                if (!columns.keySet().containsAll(Set.of("question", "answer")))
                    return failure(1, "CSV must contain a header row with question and answer columns.");
                while (records.hasNext()) {
                    CSVRecord record = records.next();
                    currentRow = position.at((int) record.getCharacterPosition());
                    if (record.size() == 1 && record.get(0).isBlank()) continue;
                    if (rows.size() == MAX_ROWS) {
                        errors.add(new CsvPreviewResponse.FileError(currentRow, "CSV must not contain more than 1000 flashcards."));
                        break;
                    }
                    if (record.size() != header.size()) {
                        rows.add(new CsvPreviewResponse.Row(currentRow, null, Map.of("row", "The number of values must match the header columns.")));
                        continue;
                    }
                    var data = new SaveFlashcardRequest(value(record, columns, "question"), value(record, columns, "answer"),
                            value(record, columns, "example_sentence"), value(record, columns, "translated_example"), value(record, columns, "image_url"));
                    var fields = new LinkedHashMap<String, String>();
                    validator.validate(data).stream().sorted(java.util.Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                            .forEach(violation -> fields.putIfAbsent(FIELDS.get(violation.getPropertyPath().toString()), violation.getMessage()));
                    rows.add(new CsvPreviewResponse.Row(currentRow, data, Map.copyOf(fields)));
                }
                if (rows.isEmpty() && errors.isEmpty()) errors.add(new CsvPreviewResponse.FileError(1, "CSV contains no flashcards."));
            } catch (UncheckedIOException exception) {
                errors.add(new CsvPreviewResponse.FileError(Math.max(currentRow, parser.getCurrentLineNumber()),
                        "Invalid CSV syntax. Check double quotes and separators near this line."));
            }
        } catch (IOException exception) { return failure(1, "Unable to read the CSV file."); }
        return CsvPreviewResponse.of(rows, errors);
    }

    private static String value(CSVRecord record, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        return index == null ? null : record.get(index);
    }
    private static CsvPreviewResponse failure(long row, String message) {
        return CsvPreviewResponse.of(List.of(), List.of(new CsvPreviewResponse.FileError(row, message)));
    }
    private static final class SourceLines {
        private final String text;
        private int offset;
        private long line = 1;
        private SourceLines(String text) { this.text = text; }
        long at(int end) {
            for (; offset < end; offset++) {
                char character = text.charAt(offset);
                if (character == '\r' || character == '\n' && (offset == 0 || text.charAt(offset - 1) != '\r')) line++;
            }
            return line;
        }
    }
}
