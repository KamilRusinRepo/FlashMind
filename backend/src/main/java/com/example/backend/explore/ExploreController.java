package com.example.backend.explore;

import com.example.backend.auth.SessionUser;
import com.example.backend.explore.dto.response.PublicDeckPageResponse;
import com.example.backend.explore.dto.response.PublicDeckResponse;
import com.example.backend.flashcard.dto.response.FlashcardPageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/explore/decks")
public class ExploreController {
    private final ExploreService explore;
    public ExploreController(ExploreService explore) { this.explore = explore; }

    @GetMapping("/{deckId}")
    public ResponseEntity<PublicDeckResponse> details(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") @Min(1) Long deckId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(explore.details(user.id(), deckId));
    }

    @GetMapping("/{deckId}/flashcards")
    public ResponseEntity<FlashcardPageResponse> flashcards(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") @Min(1) Long deckId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "12") @Min(1) @Max(48) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(explore.flashcards(user.id(), deckId, page, size));
    }

    @GetMapping
    public ResponseEntity<PublicDeckPageResponse> list(@AuthenticationPrincipal SessionUser user,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "12") @Min(1) @Max(48) int size,
            @RequestParam(name = "q", defaultValue = "") @Size(max = 120) String query,
            @RequestParam(name = "deck_type", defaultValue = "") @Size(max = 20) String type,
            @RequestParam(name = "category", defaultValue = "") @Size(max = 60) String category,
            @RequestParam(name = "source_language", defaultValue = "") @Size(max = 60) String source,
            @RequestParam(name = "target_language", defaultValue = "") @Size(max = 60) String target,
            @RequestParam(name = "sort", defaultValue = "newest") @Size(max = 20) String sort) {
        var filters = new ExploreFilters(query, type, category, source, target, sort);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(explore.list(user.id(), filters, page, size));
    }
}
