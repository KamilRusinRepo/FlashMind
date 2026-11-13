package com.example.backend.deck;

import com.example.backend.deck.dto.response.DeckListItemResponse;
import com.example.backend.deck.dto.response.DeckPageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeckService {
    private final DeckRepository decks;

    public DeckService(DeckRepository decks) { this.decks = decks; }

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
