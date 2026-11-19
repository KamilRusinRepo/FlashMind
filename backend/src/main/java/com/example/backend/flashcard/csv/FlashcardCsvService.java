package com.example.backend.flashcard.csv;

import com.example.backend.deck.DeckRepository;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.flashcard.dto.response.FlashcardResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FlashcardCsvService {
    private final DeckRepository decks;
    private final FlashcardRepository flashcards;
    private final FlashcardCsvPreviewParser previewParser;

    public FlashcardCsvService(DeckRepository decks, FlashcardRepository flashcards, FlashcardCsvPreviewParser previewParser) {
        this.decks = decks;
        this.flashcards = flashcards;
        this.previewParser = previewParser;
    }

    @Transactional(readOnly = true)
    public CsvPreviewResponse previewOwned(Long ownerId, Long deckId, MultipartFile file) {
        decks.findByIdAndOwner_Id(deckId, ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv"))
            throw new CsvPreviewException(HttpStatus.BAD_REQUEST, "Choose a file with the .csv extension.");
        if (file.getSize() > FlashcardCsvPreviewParser.MAX_BYTES)
            throw new CsvPreviewException(HttpStatus.CONTENT_TOO_LARGE, "CSV file must not exceed 1 MiB.");
        try (var input = file.getInputStream()) {
            return previewParser.parse(input.readNBytes(FlashcardCsvPreviewParser.MAX_BYTES + 1));
        } catch (IOException exception) { throw new CsvPreviewException(HttpStatus.BAD_REQUEST, "Unable to read the CSV file."); }
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
