package com.example.backend.flashcard.csv;

import com.example.backend.deck.DeckRepository;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.flashcard.dto.response.FlashcardResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FlashcardCsvService {
    private final DeckRepository decks;
    private final FlashcardRepository flashcards;

    public FlashcardCsvService(DeckRepository decks, FlashcardRepository flashcards) {
        this.decks = decks;
        this.flashcards = flashcards;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] exportOwned(Long ownerId, Long deckId) {
        // Check even empty decks. Public visibility, following and ADMIN do not confer ownership.
        decks.findByIdAndOwner_Id(deckId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var cards = flashcards.findByDeck_IdAndDeck_Owner_IdOrderByCreatedAtAscIdAsc(deckId, ownerId);
        return FlashcardCsvWriter.write(cards.stream().map(FlashcardResponse::from).toList());
    }
}
