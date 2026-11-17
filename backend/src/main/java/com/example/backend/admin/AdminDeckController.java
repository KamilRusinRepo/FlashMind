package com.example.backend.admin;

import com.example.backend.admin.dto.response.AdminDeckPageResponse;
import com.example.backend.auth.SessionUser;
import com.example.backend.deck.dto.request.SaveDeckRequest;
import com.example.backend.deck.dto.response.DeckListItemResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/decks")
public class AdminDeckController {
    private final AdminDeckService decks;
    public AdminDeckController(AdminDeckService decks) { this.decks = decks; }

    @GetMapping
    public ResponseEntity<AdminDeckPageResponse> list(@AuthenticationPrincipal SessionUser principal,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "5") @Min(1) @Max(50) int size,
            @RequestParam(name = "search", defaultValue = "") @Size(max = 120) String search,
            @RequestParam(name = "deck_type", defaultValue = "") @Size(max = 20) String type) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.list(principal.id(), page, size, search, type));
    }

    @DeleteMapping("/{deckId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SessionUser principal,
            @PathVariable(name = "deckId") @Min(1) Long deckId) {
        decks.deletePublic(principal, deckId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/{deckId}")
    public ResponseEntity<DeckListItemResponse> details(@AuthenticationPrincipal SessionUser principal,
            @PathVariable(name = "deckId") @Min(1) Long deckId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.getPublic(principal.id(), deckId));
    }

    @PutMapping("/{deckId}")
    public ResponseEntity<DeckListItemResponse> update(@AuthenticationPrincipal SessionUser principal,
            @PathVariable(name = "deckId") @Min(1) Long deckId, @Valid @RequestBody SaveDeckRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.updatePublic(principal, deckId, request));
    }
}
