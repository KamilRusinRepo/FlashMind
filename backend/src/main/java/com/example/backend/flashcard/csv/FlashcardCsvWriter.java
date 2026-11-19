package com.example.backend.flashcard.csv;

import java.nio.charset.StandardCharsets;
import java.util.List;
import com.example.backend.flashcard.dto.response.FlashcardResponse;

/** CSV interchange format: UTF-8, comma-separated, a header and CRLF record separators. */
public final class FlashcardCsvWriter {
    private FlashcardCsvWriter() { }

    public static byte[] write(List<FlashcardResponse> cards) {
        var csv = new StringBuilder("question,answer,example_sentence,translated_example,image_url\r\n");
        for (var card : cards) {
            csv.append(field(card.question())).append(',').append(field(card.answer())).append(',')
                    .append(field(card.exampleSentence())).append(',').append(field(card.translatedExample())).append(',')
                    .append(field(card.imageUrl())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String field(String value) {
        // Quote every value so commas, quotes and embedded newlines preserve the original text.
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }
}
