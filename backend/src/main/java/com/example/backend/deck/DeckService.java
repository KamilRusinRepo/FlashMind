package com.example.backend.deck;

import com.example.backend.deck.dto.response.DeckListItemResponse;
import com.example.backend.deck.dto.response.DeckPageResponse;
import com.example.backend.deck.dto.request.SaveDeckRequest;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeckService {
    private final DeckRepository decks;
    private final UserRepository users;
    private final DeckAccessService access;

    public DeckService(DeckRepository decks, UserRepository users, DeckAccessService access) { this.decks = decks; this.users = users; this.access = access; }

    @Transactional(readOnly = true)
    public DeckListItemResponse getAccessible(Long userId, Long deckId) {
        return DeckListItemResponse.from(access.requireAccessible(userId, deckId), userId);
    }

    @Transactional(readOnly = true)
    public DeckPageResponse listFollowed(Long userId, int page, int size, String search) {
        String pattern = "%" + search.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = decks.findFollowed(userId, pattern, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new DeckPageResponse(result.map(deck -> DeckListItemResponse.from(deck, userId)).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DeckPageResponse listLibrary(Long userId, int page, int size, String search) {
        String pattern = "%" + search.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = decks.findLibrary(userId, pattern, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new DeckPageResponse(result.map(deck -> DeckListItemResponse.from(deck, userId)).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public DeckListItemResponse create(Long ownerId, SaveDeckRequest request) {
        // Serialize against account deletion; the owner never comes from the request body.
        var owner = lockActiveOwner(ownerId);
        var deck = new Deck(owner, request.name(), request.deckType(), request.sourceLanguage(), request.targetLanguage());
        applyOptionalDetails(deck, request);
        return DeckListItemResponse.from(decks.saveAndFlush(deck));
    }

    @Transactional(readOnly = true)
    public DeckListItemResponse getOwned(Long ownerId, Long deckId) {
        return DeckListItemResponse.from(decks.findByIdAndOwner_Id(deckId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @Transactional
    public DeckListItemResponse updateOwned(Long ownerId, Long deckId, SaveDeckRequest request) {
        lockActiveOwner(ownerId);
        var deck = lockOwnedDeck(ownerId, deckId);
        deck.setName(request.name());
        deck.setLearningLanguages(request.deckType(), request.sourceLanguage(), request.targetLanguage());
        applyOptionalDetails(deck, request);
        return DeckListItemResponse.from(decks.saveAndFlush(deck));
    }

    @Transactional
    public void deleteOwned(Long ownerId, Long deckId) {
        lockActiveOwner(ownerId);
        var deck = lockOwnedDeck(ownerId, deckId);
        decks.delete(deck);
        decks.flush();
    }

    private User lockActiveOwner(Long ownerId) {
        var owner = users.findByIdForUpdate(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!owner.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return owner;
    }

    private Deck lockOwnedDeck(Long ownerId, Long deckId) {
        return decks.findOwnedForUpdate(deckId, ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void applyOptionalDetails(Deck deck, SaveDeckRequest request) {
        deck.setDescription(request.description());
        deck.setCategory(request.category());
        deck.setBackgroundUrl(request.backgroundUrl());
        deck.setPublic(request.publicDeck());
    }

    @Transactional(readOnly = true)
    public DeckPageResponse listOwned(Long ownerId, int page, int size, String search) {
        // Search means literal substring, not SQL wildcard syntax.
        String pattern = "%" + search.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = decks.findOwned(ownerId, pattern, pageable);
        return new DeckPageResponse(result.map(DeckListItemResponse::from).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }
}
