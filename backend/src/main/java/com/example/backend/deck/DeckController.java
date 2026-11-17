package com.example.backend.deck;

import com.example.backend.auth.SessionUser;
import com.example.backend.deck.dto.response.DeckPageResponse;
import com.example.backend.deck.dto.response.DeckListItemResponse;
import com.example.backend.deck.dto.request.SaveDeckRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/decks")
public class DeckController {
    private final DeckService decks;

    public DeckController(DeckService decks) { this.decks = decks; }

    @GetMapping("/following")
    public ResponseEntity<DeckPageResponse> following(@AuthenticationPrincipal SessionUser user,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "12") @Min(1) @Max(48) int size,
            @RequestParam(name = "search", defaultValue = "") @Size(max = 120) String search) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.listFollowed(user.id(), page, size, search));
    }

    @GetMapping("/library")
    public ResponseEntity<DeckPageResponse> library(@AuthenticationPrincipal SessionUser user,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "12") @Min(1) @Max(48) int size,
            @RequestParam(name = "search", defaultValue = "") @Size(max = 120) String search) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.listLibrary(user.id(), page, size, search));
    }

    @GetMapping("/{deckId}/study-access")
    public ResponseEntity<DeckListItemResponse> studyAccess(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") @Min(1) Long deckId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.getAccessible(user.id(), deckId));
    }

    @PostMapping
    public ResponseEntity<DeckListItemResponse> create(@AuthenticationPrincipal SessionUser user,
                                                      @Valid @RequestBody SaveDeckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(decks.create(user.id(), request));
    }

    @GetMapping("/{deckId}")
    public ResponseEntity<DeckListItemResponse> getOwned(@AuthenticationPrincipal SessionUser user,
                                                       @PathVariable(name = "deckId") Long deckId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.getOwned(user.id(), deckId));
    }

    @PutMapping("/{deckId}")
    public ResponseEntity<DeckListItemResponse> updateOwned(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") Long deckId, @Valid @RequestBody SaveDeckRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.updateOwned(user.id(), deckId, request));
    }

    @DeleteMapping("/{deckId}")
    public ResponseEntity<Void> deleteOwned(@AuthenticationPrincipal SessionUser user,
                                           @PathVariable(name = "deckId") Long deckId) {
        decks.deleteOwned(user.id(), deckId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping
    public ResponseEntity<DeckPageResponse> listOwned(@AuthenticationPrincipal SessionUser user,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "12") @Min(1) @Max(48) int size,
            @RequestParam(name = "search", defaultValue = "") @Size(max = 120) String search) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.listOwned(user.id(), page, size, search));
    }
}
