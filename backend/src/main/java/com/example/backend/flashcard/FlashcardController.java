package com.example.backend.flashcard;

import com.example.backend.auth.SessionUser;
import com.example.backend.flashcard.dto.response.FlashcardPageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decks/{deckId}/flashcards")
public class FlashcardController {
    private final FlashcardService flashcards;

    public FlashcardController(FlashcardService flashcards) { this.flashcards = flashcards; }

    @GetMapping
    public ResponseEntity<FlashcardPageResponse> listOwned(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") Long deckId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "12") @Min(1) @Max(48) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(flashcards.listOwned(user.id(), deckId, page, size));
    }
}
