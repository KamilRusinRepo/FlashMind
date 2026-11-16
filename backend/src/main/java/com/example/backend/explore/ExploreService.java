package com.example.backend.explore;

import java.util.Set;
import com.example.backend.explore.dto.response.PublicDeckPageResponse;
import com.example.backend.explore.dto.response.PublicDeckResponse;
import com.example.backend.flashcard.dto.response.FlashcardPageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExploreService {
    private final ExploreRepository decks;
    public ExploreService(ExploreRepository decks) { this.decks = decks; }

    @Transactional(readOnly = true)
    public PublicDeckResponse details(Long viewerId, Long deckId) {
        return requirePublic(viewerId, deckId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FlashcardPageResponse flashcards(Long viewerId, Long deckId, int page, int size) {
        long total = requirePublic(viewerId, deckId).cardCount();
        return new FlashcardPageResponse(decks.findPublicCards(deckId, page, size), page, size,
                total, (int) ((total + size - 1) / size));
    }

    private PublicDeckResponse requirePublic(Long viewerId, Long deckId) {
        return decks.findPublic(viewerId, deckId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PublicDeckPageResponse list(Long viewerId, ExploreFilters input, int page, int size) {
        String type = input.deckType().strip();
        String sort = input.sort().strip();
        if (!Set.of("", "GENERAL", "LANGUAGE").contains(type) || !Set.of("newest", "cards").contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var filters = new ExploreFilters(input.query().strip(), type, input.category().strip(), input.sourceLanguage().strip(),
                type.equals("LANGUAGE") ? input.targetLanguage().strip() : "", sort);
        long total = decks.count(filters);
        return new PublicDeckPageResponse(decks.find(viewerId, filters, page, size), page, size, total, (int) ((total + size - 1) / size));
    }
}
