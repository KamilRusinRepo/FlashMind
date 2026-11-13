package com.example.backend.flashcard;

import com.example.backend.deck.DeckRepository;
import com.example.backend.flashcard.dto.response.FlashcardPageResponse;
import com.example.backend.flashcard.dto.response.FlashcardResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FlashcardService {
    private final FlashcardRepository flashcards;
    private final DeckRepository decks;

    public FlashcardService(FlashcardRepository flashcards, DeckRepository decks) {
        this.flashcards = flashcards;
        this.decks = decks;
    }

    @Transactional(readOnly = true)
    public FlashcardPageResponse listOwned(Long ownerId, Long deckId, int page, int size) {
        // Check ownership even when the deck has no cards. Public is not sufficient here.
        decks.findByIdAndOwner_Id(deckId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = flashcards.findByDeck_IdAndDeck_Owner_Id(deckId, ownerId, pageable);
        return new FlashcardPageResponse(result.map(FlashcardResponse::from).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }
}
