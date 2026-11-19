package com.example.backend.flashcard.csv;

import com.example.backend.deck.DeckRepository;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.flashcard.dto.response.FlashcardResponse;
import com.example.backend.auth.SessionUser;
import com.example.backend.user.UserRepository;
import com.example.backend.flashcard.Flashcard;
import java.util.LinkedHashMap;
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
    private final UserRepository users;

    public FlashcardCsvService(DeckRepository decks, FlashcardRepository flashcards, FlashcardCsvPreviewParser previewParser, UserRepository users) {
        this.decks = decks;
        this.flashcards = flashcards;
        this.previewParser = previewParser;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CsvPreviewResponse previewOwned(Long ownerId, Long deckId, MultipartFile file) {
        decks.findByIdAndOwner_Id(deckId, ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return analyse(file);
    }

    @Transactional
    public CsvImportResponse importOwned(SessionUser principal, Long deckId, MultipartFile file, boolean confirmed) {
        // Match all other card mutations: owner first, deck second, held until commit.
        var owner = users.findByIdForUpdate(principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (owner.getSessionVersion() != principal.sessionVersion()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!owner.isEnabled() || owner.getEmailVerifiedAt() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var deck = decks.findOwnedForUpdate(deckId, principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!confirmed) throw new CsvPreviewException(HttpStatus.BAD_REQUEST, "Confirm the import before saving flashcards.");
        // Never trust a preview or card DTO from the browser. Re-read and validate the complete file.
        var result = analyse(file);
        if (!result.valid()) {
            var errors = new LinkedHashMap<String, String>();
            result.errors().forEach(error -> errors.put("line " + error.rowNumber(), error.message()));
            result.rows().forEach(row -> row.fieldErrors().forEach((field, message) -> errors.put("line " + row.rowNumber() + "." + field, message)));
            throw new CsvPreviewException(HttpStatus.BAD_REQUEST, "The CSV file is invalid. Analyse it again before importing.", errors);
        }
        var cards = result.rows().stream().map(row -> {
            var data = row.data();
            var card = new Flashcard(deck, data.question(), data.answer());
            card.setExampleSentence(data.exampleSentence());
            card.setTranslatedExample(data.translatedExample());
            card.setImageUrl(data.imageUrl());
            return card;
        }).toList();
        flashcards.saveAll(cards);
        flashcards.flush();
        return new CsvImportResponse(cards.size());
    }

    private CsvPreviewResponse analyse(MultipartFile file) {
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
