package com.example.backend.flashcard.csv;

import com.example.backend.auth.SessionUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decks/{deckId}/flashcards")
public class FlashcardCsvController {
    private final FlashcardCsvService csv;
    public FlashcardCsvController(FlashcardCsvService csv) { this.csv = csv; }

    @Operation(summary = "Export all flashcards from an owned deck as UTF-8 CSV",
            description = "Owner only, including private decks. Returns all cards in creation order, without pagination. "
                    + "Columns: question, answer, example_sentence, translated_example, image_url. Empty decks return only the header.")
    @GetMapping("/export")
    @ApiResponse(responseCode = "200", description = "Download the complete CSV file",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") @Min(1) Long deckId) {
        byte[] content = csv.exportOwned(user.id(), deckId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("deck-" + deckId + "-flashcards.csv").build().toString())
                .body(content);
    }
}
