package com.example.backend.flashcard.csv;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import com.example.backend.flashcard.dto.response.FlashcardResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlashcardCsvPreviewParserTests {
    private CsvPreviewResponse parse(String csv) {
        return parse(csv.getBytes(StandardCharsets.UTF_8));
    }
    private CsvPreviewResponse parse(byte[] csv) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return new FlashcardCsvPreviewParser(factory.getValidator()).parse(csv);
        }
    }

    @Test void readsExportedCsvWithoutLosingUnicodeQuotesOrNewlines() {
        var card = new FlashcardResponse(1L, "Hello, \"world\"", "Żółw", "One\r\nTwo", "Jeden\nDwa",
                "https://example.invalid/photo.png", Instant.EPOCH);
        var result = parse(FlashcardCsvWriter.write(List.of(card)));
        assertTrue(result.valid()); assertEquals(1, result.validRows());
        var row = result.rows().getFirst();
        assertEquals(2, row.rowNumber()); assertEquals(card.question(), row.data().question());
        assertEquals(card.answer(), row.data().answer()); assertEquals(card.exampleSentence(), row.data().exampleSentence());
        assertEquals(card.translatedExample(), row.data().translatedExample()); assertEquals(card.imageUrl(), row.data().imageUrl());
    }

    @Test void acceptsBomReorderedOptionalColumnsAndBlankLinesWithoutCollapsingDuplicates() {
        var result = parse("\uFEFFanswer,question\r\n\r\n  Tak  ,  Yes  \r\nTak,Yes\r\n");
        assertTrue(result.valid()); assertEquals(2, result.totalRows());
        assertEquals(3, result.rows().get(0).rowNumber()); assertEquals(4, result.rows().get(1).rowNumber());
        assertEquals("Yes", result.rows().getFirst().data().question());
        assertNull(result.rows().getFirst().data().imageUrl());
    }

    @Test void validatesFieldsAndColumnCountsWithPhysicalLineNumbersAfterMultilineRecords() {
        var result = parse("question,answer,image_url\n\"multi\nline\",,\nGood,Yes,javascript:alert(1)\nA,B\n");
        assertFalse(result.valid()); assertEquals(3, result.invalidRows());
        assertEquals(2, result.rows().get(0).rowNumber()); assertTrue(result.rows().get(0).fieldErrors().containsKey("answer"));
        assertEquals(4, result.rows().get(1).rowNumber()); assertTrue(result.rows().get(1).fieldErrors().containsKey("image_url"));
        assertEquals(5, result.rows().get(2).rowNumber()); assertTrue(result.rows().get(2).fieldErrors().containsKey("row"));
        assertNull(result.rows().get(2).data());
        var lengths = parse("question,answer,example_sentence\n" + "x".repeat(2001) + ",answer," + "x".repeat(1001));
        assertTrue(lengths.rows().getFirst().fieldErrors().containsKey("question"));
        assertTrue(lengths.rows().getFirst().fieldErrors().containsKey("example_sentence"));
    }

    @Test void rejectsMissingDuplicateAndUnknownHeadersAndEmptyFiles() {
        for (String csv : new String[]{"", "answer\nA", "question,question,answer\nQ,Q,A", "question,answer,unknown\nQ,A,X", "question;answer\nQ;A", "question,answer\n"}) {
            var result = parse(csv);
            assertFalse(result.valid(), csv); assertFalse(result.errors().isEmpty(), csv);
            assertEquals(1, result.errors().getFirst().rowNumber());
        }
    }

    @Test void reportsMalformedQuotesInvalidUtf8AndNullCharactersWithoutLeakingParserMessages() {
        var broken = parse("question,answer\nGood,Yes\n\"unclosed,Answer");
        assertFalse(broken.valid()); assertEquals(1, broken.rows().size());
        assertFalse(broken.errors().isEmpty()); assertTrue(broken.errors().getFirst().rowNumber() >= 3);
        assertFalse(parse(new byte[]{(byte) 0xC3, 0x28}).valid());
        var nul = parse("question,answer\nQ,A\0");
        assertEquals(2, nul.errors().getFirst().rowNumber());
    }

    @Test void boundsFileSizeAndRowCountWithoutApprovingATruncatedPreview() {
        assertThrows(CsvPreviewException.class, () -> parse(new byte[FlashcardCsvPreviewParser.MAX_BYTES + 1]));
        var exact = parse("question,answer\n" + "Q,A\n".repeat(1000));
        assertTrue(exact.valid()); assertEquals(1000, exact.totalRows());
        var over = parse("question,answer\n" + "Q,A\n".repeat(1001));
        assertFalse(over.valid()); assertEquals(1000, over.rows().size());
        assertEquals(1002, over.errors().getFirst().rowNumber());
    }
}
