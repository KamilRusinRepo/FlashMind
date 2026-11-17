package com.example.backend.deck.follow;

import com.example.backend.deck.DeckRepository;
import com.example.backend.explore.ExploreRepository;
import com.example.backend.explore.dto.response.PublicDeckResponse;
import com.example.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeckFollowService {
    private final UserRepository users;
    private final DeckRepository decks;
    private final DeckFollowRepository follows;
    private final ExploreRepository explore;
    public DeckFollowService(UserRepository users, DeckRepository decks, DeckFollowRepository follows, ExploreRepository explore) {
        this.users = users; this.decks = decks; this.follows = follows; this.explore = explore;
    }

    @Transactional
    public PublicDeckResponse follow(Long userId, Long deckId) {
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var deck = decks.findForUpdate(deckId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (deck.getOwner() != null && userId.equals(deck.getOwner().getId())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        if (!deck.isPublic()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!follows.existsByUser_IdAndDeck_Id(userId, deckId)) follows.saveAndFlush(new DeckFollow(user, deck));
        return explore.findPublic(userId, deckId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public void unfollow(Long userId, Long deckId) {
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        // Also allow removing a follow after the author has made the deck private.
        var deck = decks.findForUpdate(deckId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!deck.isPublic() && !follows.existsByUser_IdAndDeck_Id(userId, deckId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        follows.deleteByUser_IdAndDeck_Id(userId, deckId);
        follows.flush();
    }
}
