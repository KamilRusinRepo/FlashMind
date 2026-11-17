package com.example.backend.deck;

import com.example.backend.deck.follow.DeckFollowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Read/study access never grants permission to edit someone else's deck. Call inside a transaction. */
@Service
public class DeckAccessService {
    private final DeckRepository decks;
    private final DeckFollowRepository follows;
    public DeckAccessService(DeckRepository decks, DeckFollowRepository follows) { this.decks = decks; this.follows = follows; }

    public Deck requireAccessible(Long userId, Long deckId) {
        return requireAccess(userId, decks.findById(deckId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Deck lockAccessible(Long userId, Long deckId) {
        return requireAccess(userId, decks.findForUpdate(deckId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    private Deck requireAccess(Long userId, Deck deck) {
        boolean owner = deck.getOwner() != null && userId.equals(deck.getOwner().getId());
        if (!owner && !(deck.isPublic() && follows.existsByUser_IdAndDeck_Id(userId, deck.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return deck;
    }
}
