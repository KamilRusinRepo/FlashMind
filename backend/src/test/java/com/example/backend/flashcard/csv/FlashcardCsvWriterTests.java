package com.example.backend.flashcard.csv;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import com.example.backend.flashcard.dto.response.FlashcardResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlashcardCsvWriterTests {
    @Test void preservesUnicodeCommasQuotesNewlinesAndOptionalFields() {
        var card = new FlashcardResponse(1L, "Hello, \"world\"", "Żółw", "First\r\nSecond\nThird", null,
                "https://example.invalid/photo.png", Instant.EPOCH);
        String csv = new String(FlashcardCsvWriter.write(List.of(card)), StandardCharsets.UTF_8);
        assertEquals("question,answer,example_sentence,translated_example,image_url\r\n"
                + "\"Hello, \"\"world\"\"\",\"Żółw\",\"First\r\nSecond\nThird\",\"\",\"https://example.invalid/photo.png\"\r\n", csv);
    }

    @Test void exportsOnlyHeaderForAnEmptyDeck() {
        assertEquals("question,answer,example_sentence,translated_example,image_url\r\n",
                new String(FlashcardCsvWriter.write(List.of()), StandardCharsets.UTF_8));
    }
}
