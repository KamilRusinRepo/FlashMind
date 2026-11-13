package com.example.backend.deck;

import com.example.backend.deck.dto.response.DeckListItemResponse;
import com.example.backend.deck.dto.response.DeckPageResponse;
import com.example.backend.deck.dto.request.CreateDeckRequest;
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

    public DeckService(DeckRepository decks, UserRepository users) { this.decks = decks; this.users = users; }

    @Transactional
    public DeckListItemResponse create(Long ownerId, CreateDeckRequest request) {
        // Serialize against account deletion; the owner never comes from the request body.
        var owner = users.findByIdForUpdate(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!owner.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var deck = new Deck(owner, request.name(), request.deckType(), request.sourceLanguage(), request.targetLanguage());
        deck.setDescription(request.description());
        deck.setCategory(request.category());
        deck.setBackgroundUrl(request.backgroundUrl());
        deck.setPublic(request.publicDeck());
        return DeckListItemResponse.from(decks.saveAndFlush(deck));
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
