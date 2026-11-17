package com.example.backend.deck.follow;

import com.example.backend.auth.SessionUser;
import com.example.backend.explore.dto.response.PublicDeckResponse;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/explore/decks/{deckId}/follow")
public class DeckFollowController {
    private final DeckFollowService follows;
    public DeckFollowController(DeckFollowService follows) { this.follows = follows; }

    @PutMapping
    public ResponseEntity<PublicDeckResponse> follow(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") @Min(1) Long deckId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(follows.follow(user.id(), deckId));
    }

    @DeleteMapping
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") @Min(1) Long deckId) {
        follows.unfollow(user.id(), deckId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
