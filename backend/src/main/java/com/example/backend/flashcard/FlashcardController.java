package com.example.backend.flashcard;

import com.example.backend.auth.SessionUser;
import com.example.backend.flashcard.dto.request.SaveFlashcardRequest;
import com.example.backend.flashcard.dto.response.FlashcardResponse;
import jakarta.validation.Valid;
import com.example.backend.flashcard.dto.response.FlashcardPageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/decks/{deckId}/flashcards")
public class FlashcardController {
    private final FlashcardService flashcards;

    public FlashcardController(FlashcardService flashcards) { this.flashcards = flashcards; }

    @PutMapping("/{cardId}")
    public ResponseEntity<FlashcardResponse> updateOwned(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") Long deckId, @PathVariable(name = "cardId") Long cardId,
            @Valid @RequestBody SaveFlashcardRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(flashcards.updateOwned(user.id(), deckId, cardId, request));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> deleteOwned(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") Long deckId, @PathVariable(name = "cardId") Long cardId) {
        flashcards.deleteOwned(user.id(), deckId, cardId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping
    public ResponseEntity<FlashcardResponse> create(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") Long deckId, @Valid @RequestBody SaveFlashcardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(flashcards.create(user.id(), deckId, request));
    }

    @GetMapping
    public ResponseEntity<FlashcardPageResponse> listOwned(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") Long deckId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "12") @Min(1) @Max(48) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(flashcards.listOwned(user.id(), deckId, page, size));
    }
}
