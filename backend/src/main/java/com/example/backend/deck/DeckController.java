package com.example.backend.deck;

import com.example.backend.auth.SessionUser;
import com.example.backend.deck.dto.response.DeckPageResponse;
import com.example.backend.deck.dto.response.DeckListItemResponse;
import com.example.backend.deck.dto.request.CreateDeckRequest;
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

@RestController
@RequestMapping("/api/decks")
public class DeckController {
    private final DeckService decks;

    public DeckController(DeckService decks) { this.decks = decks; }

    @PostMapping
    public ResponseEntity<DeckListItemResponse> create(@AuthenticationPrincipal SessionUser user,
                                                      @Valid @RequestBody CreateDeckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(decks.create(user.id(), request));
    }

    @GetMapping
    public ResponseEntity<DeckPageResponse> listOwned(@AuthenticationPrincipal SessionUser user,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "12") @Min(1) @Max(48) int size,
            @RequestParam(name = "search", defaultValue = "") @Size(max = 120) String search) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.listOwned(user.id(), page, size, search));
    }
}
