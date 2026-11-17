package com.example.backend.admin;

import java.util.Set;
import com.example.backend.admin.dto.response.AdminDeckPageResponse;
import com.example.backend.auth.SessionUser;
import com.example.backend.deck.DeckRepository;
import com.example.backend.explore.ExploreFilters;
import com.example.backend.explore.ExploreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminDeckService {
    private final ExploreRepository publicDecks;
    private final DeckRepository decks;
    private final AdminUserRepository summary;
    private final AdminActorService actor;
    public AdminDeckService(ExploreRepository publicDecks, DeckRepository decks, AdminUserRepository summary, AdminActorService actor) {
        this.publicDecks = publicDecks; this.decks = decks; this.summary = summary; this.actor = actor;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminDeckPageResponse list(Long viewerId, int page, int size, String search, String type) {
        type = type.strip();
        if (!Set.of("", "GENERAL", "LANGUAGE").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var filters = new ExploreFilters(search.strip(), type, "", "", "", "newest");
        long total = publicDecks.count(filters);
        return new AdminDeckPageResponse(publicDecks.find(viewerId, filters, page, size), page, size, total,
                (int) ((total + size - 1) / size), summary.summary());
    }

    @Transactional
    public void deletePublic(SessionUser principal, Long deckId) {
        // The same user -> deck lock order as study, owner mutations and following.
        actor.requireActiveForUpdate(principal);
        var deck = decks.findForUpdate(deckId).filter(value -> value.isPublic())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        decks.delete(deck);
        decks.flush();
    }
}
